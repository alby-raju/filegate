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

package dev.filegate.worker.internal.process;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.filegate.worker.process.WorkerFailure;
import dev.filegate.worker.process.WorkerLimits;
import dev.filegate.worker.process.WorkerRun;
import dev.filegate.worker.protocol.ProtocolBinding;
import dev.filegate.worker.protocol.WorkerProtocol;
import dev.filegate.worker.protocol.WorkerResponse;

/** Owns the three bounded stream tasks for exactly one worker process. */
public final class WorkerExchange implements AutoCloseable {
    private final Process process;
    private final Duration exitObservation;
    private final List<Future<?>> tasks = new ArrayList<>(3);
    private final AtomicBoolean stderrExceeded = new AtomicBoolean();
    private final Future<?> writer;
    private final Future<?> stderr;
    private final Future<WorkerResponse> reader;

    public WorkerExchange(ThreadPoolExecutor executor, Process process, ProtocolBinding binding,
                          InputStream payload, WorkerLimits limits) {
        this.process = process;
        this.exitObservation = limits.terminationGrace();
        try {
            writer = executor.submit(() -> {
                try (var output = process.getOutputStream()) {
                    WorkerProtocol.writeRequest(output, binding);
                    if (payload != null) writePayload(payload, output, binding.artifact().length());
                }
                return null;
            });
            tasks.add(writer);
            stderr = executor.submit(() -> {
                drainDiagnostics(limits.maxStderrBytes());
                return null;
            });
            tasks.add(stderr);
            reader = executor.submit(() -> {
                try (var input = process.getInputStream()) {
                    var response = WorkerProtocol.readResponse(input, binding);
                    if (input.read() != -1) throw new IOException("Trailing worker output");
                    return response;
                }
            });
            tasks.add(reader);
        } catch (RuntimeException rejected) {
            close();
            throw rejected;
        }
    }

    public WorkerRun await(TaskDeadline deadline) throws TimeoutException, InterruptedException {
        try {
            WorkerResponse response = reader.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            writer.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            stderr.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            if (!process.waitFor(deadline.remainingNanos(), TimeUnit.NANOSECONDS)) throw new TimeoutException();
            return process.exitValue() == 0 ? new WorkerRun.Completed(response) : new WorkerRun.Failed(WorkerFailure.CRASH);
        } catch (ExecutionException invalid) {
            if (stderrExceeded.get()) return new WorkerRun.Failed(WorkerFailure.STDERR_LIMIT);
            if (invalid.getCause() instanceof EOFException) {
                long wait = Math.min(deadline.remainingNanos(), exitObservation.toNanos());
                // EOF can precede Process exit notification. Observe natural exit before our cleanup kill.
                if (process.waitFor(wait, TimeUnit.NANOSECONDS) && process.exitValue() != 0) {
                    return new WorkerRun.Failed(WorkerFailure.CRASH);
                }
            }
            return new WorkerRun.Failed(WorkerFailure.PROTOCOL);
        }
    }

    private void drainDiagnostics(int allowance) throws IOException {
        try (var errors = process.getErrorStream()) {
            byte[] buffer = new byte[1024];
            int remaining = allowance;
            for (int read; (read = errors.read(buffer)) != -1;) {
                if (read > remaining) {
                    stderrExceeded.set(true);
                    process.destroyForcibly();
                    throw new IOException("Worker diagnostic limit exceeded");
                }
                remaining -= read;
            }
        }
    }

    private static void writePayload(InputStream source, OutputStream destination, long expected) throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        for (int read; (read = source.read(buffer)) != -1;) {
            if (read > expected - count) throw new IOException("Payload length mismatch");
            count += read;
            destination.write(buffer, 0, read);
        }
        if (count != expected) throw new IOException("Payload length mismatch");
    }

    @Override public void close() { tasks.forEach(task -> task.cancel(true)); }
}
