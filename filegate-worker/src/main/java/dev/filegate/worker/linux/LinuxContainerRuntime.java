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
package dev.filegate.worker.linux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.filegate.worker.internal.process.RestartBackoff;
import dev.filegate.worker.process.WorkerFailure;
import dev.filegate.worker.process.WorkerLimits;
import dev.filegate.worker.process.WorkerPool;
import dev.filegate.worker.process.WorkerRun;
import dev.filegate.worker.protocol.ProtocolBinding;

/**
 * Owns container admission, verified startup and confirmed removal; never falls
 * back to embedded parsing.
 */
public final class LinuxContainerRuntime implements AutoCloseable {
	private final ContainerSpec spec;
	private final WorkerPool pool;
	private final Semaphore admission;
	private final Path configuration;
	private final DockerCommands commands;
	private final DockerControl control;
	private final RestartBackoff backoff;
	private final Set<String> active = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean closed = new AtomicBoolean();

	private LinuxContainerRuntime(Path parent, ContainerSpec spec, WorkerLimits workers) throws IOException {
		if (!System.getProperty("os.name").equals("Linux") || !Files.isExecutable(spec.dockerBinary())) {
			throw new IOException("Supported local Linux Docker runtime is unavailable");
		}
		this.spec = spec;
		configuration = Files.createTempDirectory(parent, "filegate-docker-",
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
		commands = new DockerCommands(spec, configuration);
		control = new DockerControl(workers.taskTimeout());
		backoff = new RestartBackoff(workers.failureBackoff());
		pool = new WorkerPool(workers);
		admission = new Semaphore(workers.maxTasks());
	}

	public static LinuxContainerRuntime open(Path parent, ContainerSpec spec, WorkerLimits workers) throws IOException {
		var runtime = new LinuxContainerRuntime(parent, spec, workers);
		String name = "filegate-probe-" + UUID.randomUUID();
		runtime.active.add(name);
		var arguments = new ArrayList<>(List.of(LinuxContainmentProbe.class.getName()));
		arguments.addAll(List.of(spec.limits().arguments()));
		boolean created = runtime.control.succeeds(runtime.commands.create(name, arguments));
		boolean passed = created && runtime.control.succeeds(runtime.commands.start(name, false));
		boolean removed = runtime.control.succeeds(runtime.commands.remove(name));
		if (removed)
			runtime.active.remove(name);
		if (!passed || !removed) {
			runtime.close();
			throw new IOException("Containment probe or confirmed cleanup did not pass");
		}
		return runtime;
	}

	public WorkerRun execute(ProtocolBinding binding, InputStream capturedInput, List<String> javaArguments) {
		if (closed.get())
			return new WorkerRun.Failed(WorkerFailure.CLOSED);
		if (backoff.active())
			return new WorkerRun.Failed(WorkerFailure.BACKOFF);
		String name = "filegate-task-" + UUID.randomUUID();
		List<String> creation = commands.create(name, javaArguments);
		if (!admission.tryAcquire())
			return new WorkerRun.Failed(WorkerFailure.CAPACITY);
		active.add(name);
		WorkerRun run;
		boolean attempted = false;
		boolean removed = false;
		try {
			if (closed.get()) {
				run = new WorkerRun.Failed(WorkerFailure.CLOSED);
			} else {
				attempted = true;
				boolean created = control.succeeds(creation);
				run = created && !closed.get() ? pool.execute(binding, commands.start(name, true), capturedInput)
						: new WorkerRun.Failed(WorkerFailure.LAUNCH_FAILED);
			}
		} finally {
			// Removing the container, rather than only its CLI, revokes all task writers.
			if (attempted)
				removed = control.succeeds(commands.remove(name));
			if (attempted && !removed) {
				closed.set(true);
				pool.close();
			} else {
				active.remove(name);
				admission.release();
				cleanControlDirectory();
			}
		}
		if (attempted && !removed)
			run = new WorkerRun.Failed(WorkerFailure.TERMINATION_FAILED);
		if (!run.succeeded())
			backoff.recordFailure();
		return run;
	}

	public SandboxLimits limits() {
		return spec.limits();
	}

	public String image() {
		return spec.image();
	}

	private void cleanControlDirectory() {
		if (closed.get() && active.isEmpty()) {
			try {
				Files.deleteIfExists(configuration);
			} catch (IOException ignored) {
				/* Retain for host reconciliation. */
			}
		}
	}

	/** In-flight calls retain cleanup ownership; new calls are refused. */
	@Override
	public void close() {
		closed.set(true);
		pool.close();
		cleanControlDirectory();
	}
}
