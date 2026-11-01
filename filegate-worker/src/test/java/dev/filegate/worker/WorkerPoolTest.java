package dev.filegate.worker;

import dev.filegate.core.ArtifactIdentity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkerPoolTest {
    private ProtocolBinding binding() {
        return new ProtocolBinding(UUID.randomUUID(), new ArtifactIdentity(0, "a".repeat(64)),
                "b".repeat(64), "stub-1", List.of("a"));
    }

    private List<String> command(String mode) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), WorkerStubMain.class.getName(), mode);
    }

    private WorkerLimits limits(Duration timeout, Duration backoff) {
        return new WorkerLimits(1, timeout, Duration.ofSeconds(2), backoff, 1024);
    }

    @Test void validTaskReturnsBoundEvidenceAndRecyclesItsSlot() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            for (int i = 0; i < 2; i++) {
                var result = pool.execute(binding(), command("pass"));
                assertTrue(result.succeeded(), () -> "Failure: " + result.failure());
                assertEquals(0, pool.activeTasks());
                assertEquals(0, pool.activeProcesses());
            }
        }
    }

    @Test void crashesMalformedRepliesAndExtraOutputCannotSucceed() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            assertEquals(WorkerFailure.CRASH, pool.execute(binding(), command("crash")).failure());
            assertEquals(WorkerFailure.CRASH, pool.execute(binding(), command("eof-crash")).failure());
            assertEquals(WorkerFailure.PROTOCOL, pool.execute(binding(), command("bad")).failure());
            assertEquals(WorkerFailure.PROTOCOL, pool.execute(binding(), command("trailing")).failure());
            assertEquals(0, pool.activeProcesses());
        }
    }

    @Test void deadlineKillsHangAndLateReplyCannotBeReused() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(1), Duration.ZERO))) {
            assertEquals(WorkerFailure.TIMEOUT, pool.execute(binding(), command("hang")).failure());
            assertEquals(0, pool.activeProcesses());
            assertEquals(WorkerFailure.TIMEOUT, pool.execute(binding(), command("late-exit")).failure());
            assertEquals(0, pool.activeProcesses());
        }
    }

    @Test void stderrIsBoundedAndNeverIncludedInResults() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            assertEquals(WorkerFailure.STDERR_LIMIT, pool.execute(binding(), command("stderr")).failure());
            assertEquals(0, pool.activeProcesses());
        }
    }

    @Test void saturationRejectsWithoutAQueueAndShutdownKillsActiveWork() throws Exception {
        var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO));
        try (var caller = Executors.newSingleThreadExecutor()) {
            var work = caller.submit(() -> pool.execute(binding(), command("hang")));
            try {
                long stop = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (pool.activeProcesses() == 0 && System.nanoTime() < stop) Thread.sleep(5);
                assertEquals(1, pool.activeTasks());
                assertEquals(WorkerFailure.CAPACITY, pool.execute(binding(), command("pass")).failure());
            } finally { pool.close(); }
            assertFalse(work.get(5, TimeUnit.SECONDS).succeeded());
            assertEquals(0, pool.activeProcesses());
            assertEquals(WorkerFailure.CLOSED, pool.execute(binding(), command("pass")).failure());
        } finally { pool.close(); }
    }

    @Test void backoffBlocksRestartStormsAndCancellationDoesNotLaunch() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ofSeconds(30)))) {
            assertEquals(WorkerFailure.CRASH, pool.execute(binding(), command("crash")).failure());
            assertEquals(WorkerFailure.BACKOFF, pool.execute(binding(), command("pass")).failure());
        }
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            Thread.currentThread().interrupt();
            try {
                assertEquals(WorkerFailure.CANCELLED, pool.execute(binding(), command("pass")).failure());
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(0, pool.activeProcesses());
            } finally { Thread.interrupted(); }
        }
    }
}
