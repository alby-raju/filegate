/*
 * FileGate
 * Policy-based attachment inspection and verified release.
 *
 * Copyright 2026 alby-raju
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.filegate.worker;

import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded task supervisor: one disposable process per admitted slot, no admission
 * queue. This class is not an OS sandbox. Production commands must be supplied by
 * a validated containment runtime; plain JVM commands are suitable only for tests.
 */
public final class WorkerPool implements AutoCloseable {
    private final WorkerLimits limits;
    private final Semaphore slots;
    private final ThreadPoolExecutor io;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Process> active = ConcurrentHashMap.newKeySet();
    private volatile long lastFailureNanos;

    public WorkerPool(WorkerLimits limits) {
        this.limits = java.util.Objects.requireNonNull(limits, "limits");
        slots = new Semaphore(limits.maxTasks());
        int threads = limits.maxTasks() * 3;
        io = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(threads),
                Thread.ofPlatform().daemon(true).name("filegate-worker-io-", 0).factory());
    }

    public int activeTasks() { return limits.maxTasks() - slots.availablePermits(); }
    public int activeProcesses() { return active.size(); }

    public WorkerRun execute(ProtocolBinding binding, List<String> command) {
        return execute(binding, command, null);
    }

    /** Payload must be a bounded captured-file stream; caller owns and closes it. */
    public WorkerRun execute(ProtocolBinding binding, List<String> command, InputStream payload) {
        validateCommand(command);
        if (closed.get()) return failed(WorkerFailure.CLOSED);
        if (Thread.currentThread().isInterrupted()) return failed(WorkerFailure.CANCELLED);
        if (lastFailureNanos != 0 && System.nanoTime() - lastFailureNanos < limits.failureBackoff().toNanos()) {
            return failed(WorkerFailure.BACKOFF);
        }
        if (!slots.tryAcquire()) return failed(WorkerFailure.CAPACITY);
        long started = System.nanoTime();
        Process process = null;
        Future<?> writer = null;
        Future<?> stderr = null;
        Future<WorkerResponse> reader = null;
        WorkerResponse response = null;
        WorkerFailure failure = null;
        var stderrExceeded = new AtomicBoolean();
        boolean terminated = true;
        try {
            if (closed.get()) return failed(WorkerFailure.CLOSED);
            var builder = new ProcessBuilder(List.copyOf(command));
            builder.environment().clear();
            process = builder.start();
            active.add(process);
            Process worker = process;
            writer = io.submit(() -> {
                try (var output = worker.getOutputStream()) {
                    WorkerProtocol.writeRequest(output, binding);
                    if (payload != null) {
                        byte[] buffer = new byte[8192];
                        long count = 0;
                        for (int read; (read = payload.read(buffer)) != -1;) {
                            if (read > binding.artifact().length() - count) throw new IOException("Payload length mismatch");
                            count += read;
                            output.write(buffer, 0, read);
                        }
                        if (count != binding.artifact().length()) throw new IOException("Payload length mismatch");
                    }
                }
                return null;
            });
            stderr = io.submit(() -> {
                try (var errors = worker.getErrorStream()) {
                    byte[] buffer = new byte[1024];
                    int remaining = limits.maxStderrBytes();
                    for (int read; (read = errors.read(buffer)) != -1;) {
                        if (read > remaining) {
                            stderrExceeded.set(true);
                            worker.destroyForcibly();
                            throw new IOException("Worker diagnostic limit exceeded");
                        }
                        remaining -= read;
                    }
                }
                return null;
            });
            reader = io.submit(() -> {
                try (var input = worker.getInputStream()) {
                    var received = WorkerProtocol.readResponse(input, binding);
                    // A second frame, duplicate reply or retained stdout prevents success.
                    if (input.read() != -1) throw new IOException("Trailing worker output");
                    return received;
                }
            });
            response = reader.get(remaining(started), TimeUnit.NANOSECONDS);
            writer.get(remaining(started), TimeUnit.NANOSECONDS);
            stderr.get(remaining(started), TimeUnit.NANOSECONDS);
            if (!worker.waitFor(remaining(started), TimeUnit.NANOSECONDS)) throw new TimeoutException();
            if (worker.exitValue() != 0) failure = WorkerFailure.CRASH;
        } catch (TimeoutException timeout) {
            failure = WorkerFailure.TIMEOUT;
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            failure = WorkerFailure.CANCELLED;
        } catch (ExecutionException invalid) {
            failure = stderrExceeded.get() ? WorkerFailure.STDERR_LIMIT : WorkerFailure.PROTOCOL;
            if (!stderrExceeded.get() && process != null && invalid.getCause() instanceof EOFException) {
                try {
                    // Pipe EOF may precede the Process exit notification. Observe natural exit
                    // before cleanup so our own kill cannot be misclassified as a worker crash.
                    if (process.waitFor(Math.min(remaining(started), limits.terminationGrace().toNanos()), TimeUnit.NANOSECONDS)
                            && process.exitValue() != 0) failure = WorkerFailure.CRASH;
                } catch (TimeoutException timeout) {
                    failure = WorkerFailure.TIMEOUT;
                } catch (InterruptedException cancelled) {
                    Thread.currentThread().interrupt();
                    failure = WorkerFailure.CANCELLED;
                }
            }
        } catch (IOException unavailable) {
            failure = WorkerFailure.LAUNCH_FAILED;
        } catch (RuntimeException unavailable) {
            failure = WorkerFailure.CLOSED;
        } finally {
            if (process != null) {
                terminated = terminate(process);
                if (terminated) active.remove(process);
            }
            if (writer != null) writer.cancel(true);
            if (reader != null) reader.cancel(true);
            if (stderr != null) stderr.cancel(true);
            if (terminated) slots.release();
            else closed.set(true); // Quarantine the domain instead of recycling an unconfirmed process.
        }
        if (!terminated) failure = WorkerFailure.TERMINATION_FAILED;
        if (failure != null) {
            lastFailureNanos = System.nanoTime();
            return failed(failure);
        }
        return new WorkerRun(response, null);
    }

    private long remaining(long started) throws TimeoutException {
        long left = limits.taskTimeout().toNanos() - (System.nanoTime() - started);
        if (left <= 0) throw new TimeoutException();
        return left;
    }

    private boolean terminate(Process process) {
        // Best-effort tree cleanup for transport tests. OS containment must control escaped descendants.
        List<ProcessHandle> children;
        boolean descendantsVisible = true;
        try {
            children = process.descendants().toList();
        } catch (RuntimeException unavailable) {
            // Still kill the known process; never certify termination when enumeration was denied.
            children = List.of();
            descendantsVisible = false;
        }
        children.forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        try {
            long stop = System.nanoTime() + limits.terminationGrace().toNanos();
            if (!process.waitFor(limits.terminationGrace().toNanos(), TimeUnit.NANOSECONDS)) return false;
            for (ProcessHandle child : children) {
                if (child.isAlive()) {
                    long left = stop - System.nanoTime();
                    if (left <= 0) return false;
                    try { child.onExit().get(left, TimeUnit.NANOSECONDS); }
                    catch (ExecutionException | TimeoutException failure) { return false; }
                }
            }
            return descendantsVisible && children.stream().noneMatch(ProcessHandle::isAlive);
        } catch (InterruptedException cancellation) {
            interrupted = true;
            return false;
        } finally {
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static WorkerRun failed(WorkerFailure failure) { return new WorkerRun(null, failure); }

    private static void validateCommand(List<String> command) {
        if (command.isEmpty() || command.size() > 64 || !Path.of(command.getFirst()).isAbsolute()
                || command.stream().anyMatch(arg -> arg == null || arg.length() > 4096)
                || command.stream().mapToInt(String::length).sum() > 16 * 1024) {
            throw new IllegalArgumentException("Invalid bounded absolute worker command");
        }
    }

    @Override public void close() {
        closed.set(true);
        active.forEach(this::terminate);
        io.shutdownNow();
    }
}
