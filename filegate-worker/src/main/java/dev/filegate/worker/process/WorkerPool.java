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
package dev.filegate.worker.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.filegate.worker.internal.process.ProcessTermination;
import dev.filegate.worker.internal.process.RestartBackoff;
import dev.filegate.worker.internal.process.TaskDeadline;
import dev.filegate.worker.internal.process.WorkerExchange;
import dev.filegate.worker.protocol.ProtocolBinding;

/**
 * Bounded admission and task lifecycle. This supervisor alone is not an OS
 * sandbox.
 */
public final class WorkerPool implements AutoCloseable {
	private final WorkerLimits limits;
	private final Semaphore slots;
	private final ThreadPoolExecutor io;
	private final ProcessTermination termination;
	private final RestartBackoff backoff;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final Set<Process> active = ConcurrentHashMap.newKeySet();

	public WorkerPool(WorkerLimits limits) {
		this.limits = Objects.requireNonNull(limits, "limits");
		slots = new Semaphore(limits.maxTasks());
		termination = new ProcessTermination(limits.terminationGrace());
		backoff = new RestartBackoff(limits.failureBackoff());
		int threads = limits.maxTasks() * 3;
		io = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(threads),
				Thread.ofPlatform().daemon(true).name("filegate-worker-io-", 0).factory());
	}

	public int activeTasks() {
		return limits.maxTasks() - slots.availablePermits();
	}

	public int activeProcesses() {
		return active.size();
	}

	public WorkerRun execute(ProtocolBinding binding, List<String> command) {
		return execute(binding, command, null);
	}

	/**
	 * Payload must be captured-file input. The caller owns its lifetime and
	 * transport timeout.
	 */
	public WorkerRun execute(ProtocolBinding binding, List<String> command, InputStream payload) {
		Objects.requireNonNull(binding, "binding");
		validateCommand(command);
		if (closed.get())
			return new WorkerRun.Failed(WorkerFailure.CLOSED);
		if (Thread.currentThread().isInterrupted())
			return new WorkerRun.Failed(WorkerFailure.CANCELLED);
		if (backoff.active())
			return new WorkerRun.Failed(WorkerFailure.BACKOFF);
		if (!slots.tryAcquire())
			return new WorkerRun.Failed(WorkerFailure.CAPACITY);

		var deadline = new TaskDeadline(limits.taskTimeout());
		Process process = null;
		boolean terminated = true;
		WorkerRun run;
		try {
			if (closed.get())
				return new WorkerRun.Failed(WorkerFailure.CLOSED);
			var builder = new ProcessBuilder(List.copyOf(command));
			builder.environment().clear();
			process = builder.start();
			active.add(process);
			try (var exchange = new WorkerExchange(io, process, binding, payload, limits)) {
				run = exchange.await(deadline);
			}
		} catch (TimeoutException timeout) {
			run = new WorkerRun.Failed(WorkerFailure.TIMEOUT);
		} catch (InterruptedException cancelled) {
			Thread.currentThread().interrupt();
			run = new WorkerRun.Failed(WorkerFailure.CANCELLED);
		} catch (IOException unavailable) {
			run = new WorkerRun.Failed(WorkerFailure.LAUNCH_FAILED);
		} catch (RuntimeException unavailable) {
			run = new WorkerRun.Failed(WorkerFailure.CLOSED);
		} finally {
			if (process != null) {
				terminated = termination.terminate(process);
				if (terminated)
					active.remove(process);
			}
			io.purge();
			if (terminated)
				slots.release();
			else
				closed.set(true);
		}
		if (!terminated)
			run = new WorkerRun.Failed(WorkerFailure.TERMINATION_FAILED);
		if (!run.succeeded())
			backoff.recordFailure();
		return run;
	}

	private static void validateCommand(List<String> command) {
		Objects.requireNonNull(command, "command");
		if (command.isEmpty() || command.size() > 64
				|| command.stream().anyMatch(arg -> arg == null || arg.length() > 4096)
				|| command.stream().mapToInt(String::length).sum() > 16 * 1024
				|| !Path.of(command.getFirst()).isAbsolute()) {
			throw new IllegalArgumentException("Invalid bounded absolute worker command");
		}
	}

	@Override
	public void close() {
		closed.set(true);
		active.forEach(termination::terminate);
		io.shutdownNow();
	}
}
