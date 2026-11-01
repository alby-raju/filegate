package dev.filegate.worker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local Linux Docker/cgroup-v2 runtime. No embedded or remote-daemon fallback. */
public final class LinuxContainerRuntime implements AutoCloseable {
    private final ContainerSpec spec;
    private final WorkerPool pool;
    private final Semaphore admission;
    private final Path config;
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Duration controlTimeout;
    private final Duration failureBackoff;
    private volatile long lastFailureNanos;

    private LinuxContainerRuntime(Path parent, ContainerSpec spec, WorkerLimits workers) throws IOException {
        if (!System.getProperty("os.name").equals("Linux") || !Files.isExecutable(spec.dockerBinary())) {
            throw new IOException("Supported local Linux Docker runtime is unavailable");
        }
        this.spec = spec;
        this.controlTimeout = workers.taskTimeout();
        this.failureBackoff = workers.failureBackoff();
        config = Files.createTempDirectory(parent, "filegate-docker-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        pool = new WorkerPool(workers);
        admission = new Semaphore(workers.maxTasks());
    }

    /** Runs an actual kernel-restriction probe in the exact immutable worker image before returning. */
    public static LinuxContainerRuntime open(Path parent, ContainerSpec spec, WorkerLimits workers) throws IOException {
        var runtime = new LinuxContainerRuntime(parent, spec, workers);
        String name = "filegate-probe-" + UUID.randomUUID();
        runtime.active.add(name);
        var args = new ArrayList<>(List.of(LinuxContainmentProbe.class.getName()));
        args.addAll(List.of(spec.limits().arguments()));
        boolean created = runtime.admin(spec.createCommand(runtime.config, name, args));
        boolean passed = created && runtime.admin(runtime.command("start", "--attach", name));
        boolean removed = runtime.admin(runtime.command("rm", "--force", name));
        if (removed) runtime.active.remove(name);
        if (!passed || !removed) {
            runtime.close();
            throw new IOException("Containment probe or confirmed cleanup did not pass");
        }
        return runtime;
    }

    /** Java arguments must select the validated worker main and its bounded profile, never untrusted metadata. */
    public WorkerRun execute(ProtocolBinding binding, InputStream capturedInput, List<String> javaArgs) {
        if (closed.get()) return new WorkerRun(null, WorkerFailure.CLOSED);
        if (lastFailureNanos != 0 && System.nanoTime() - lastFailureNanos < failureBackoff.toNanos()) {
            return new WorkerRun(null, WorkerFailure.BACKOFF);
        }
        if (!admission.tryAcquire()) return new WorkerRun(null, WorkerFailure.CAPACITY);
        String name = "filegate-task-" + UUID.randomUUID();
        active.add(name);
        WorkerRun run;
        boolean created = false;
        boolean removed = false;
        boolean attempted = false;
        try {
            if (closed.get()) {
                run = new WorkerRun(null, WorkerFailure.CLOSED);
            } else {
                attempted = true;
                created = admin(spec.createCommand(config, name, javaArgs));
                run = created && !closed.get()
                        ? pool.execute(binding, command("start", "--attach", "--interactive", name), capturedInput)
                        : new WorkerRun(null, WorkerFailure.LAUNCH_FAILED);
            }
        } finally {
            // Container removal, not killing the Docker CLI, revokes all task processes and writable state.
            if (attempted) removed = admin(command("rm", "--force", name));
            if (attempted && !removed) {
                closed.set(true); // Retain slot and metadata for reconciliation; no new admissions.
                pool.close();
            } else {
                active.remove(name);
                admission.release();
                cleanControlDirectory();
            }
        }
        if (attempted && !removed) run = new WorkerRun(null, WorkerFailure.TERMINATION_FAILED);
        if (!run.succeeded()) lastFailureNanos = System.nanoTime();
        return run;
    }

    public SandboxLimits limits() { return spec.limits(); }
    public String image() { return spec.image(); }

    private List<String> command(String... args) {
        var command = new ArrayList<>(List.of(spec.dockerBinary().toString(), "--config", config.toString(),
                "--host", "unix:///var/run/docker.sock"));
        command.addAll(List.of(args));
        return command;
    }

    private boolean admin(List<String> command) {
        Process process = null;
        boolean interrupted = Thread.interrupted();
        try {
            var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            process = builder.start();
            process.getOutputStream().close();
            return process.waitFor(controlTimeout.toNanos(), TimeUnit.NANOSECONDS) && process.exitValue() == 0;
        } catch (IOException unavailable) {
            return false;
        } catch (InterruptedException cancelled) {
            interrupted = true;
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException cancelled) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void cleanControlDirectory() {
        if (closed.get() && active.isEmpty()) {
            try { Files.deleteIfExists(config); } catch (IOException ignored) { /* Retain for host reconciliation. */ }
        }
    }

    /** Active calls finish cleanup; no new calls are admitted. */
    @Override public void close() {
        closed.set(true);
        pool.close();
        cleanControlDirectory();
    }
}
