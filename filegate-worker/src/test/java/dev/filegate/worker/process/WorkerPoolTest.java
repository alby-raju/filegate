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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.worker.protocol.ProtocolBinding;
import dev.filegate.worker.testing.WorkerStubMain;

import static dev.filegate.worker.testing.WorkerFixtures.failure;
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
                assertTrue(result.succeeded(), () -> "Failure: " + failure(result));
                assertEquals(0, pool.activeTasks());
                assertEquals(0, pool.activeProcesses());
            }
        }
    }

    @Test void crashesMalformedRepliesAndExtraOutputCannotSucceed() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            assertEquals(WorkerFailure.CRASH, failure(pool.execute(binding(), command("crash"))));
            assertEquals(WorkerFailure.CRASH, failure(pool.execute(binding(), command("eof-crash"))));
            assertEquals(WorkerFailure.PROTOCOL, failure(pool.execute(binding(), command("bad"))));
            assertEquals(WorkerFailure.PROTOCOL, failure(pool.execute(binding(), command("trailing"))));
            assertEquals(0, pool.activeProcesses());
        }
    }

    @Test void deadlineKillsHangAndLateReplyCannotBeReused() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(1), Duration.ZERO))) {
            assertEquals(WorkerFailure.TIMEOUT, failure(pool.execute(binding(), command("hang"))));
            assertEquals(0, pool.activeProcesses());
            assertEquals(WorkerFailure.TIMEOUT, failure(pool.execute(binding(), command("late-exit"))));
            assertEquals(0, pool.activeProcesses());
        }
    }

    @Test void stderrIsBoundedAndNeverIncludedInResults() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            assertEquals(WorkerFailure.STDERR_LIMIT, failure(pool.execute(binding(), command("stderr"))));
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
                assertEquals(WorkerFailure.CAPACITY, failure(pool.execute(binding(), command("pass"))));
            } finally { pool.close(); }
            assertFalse(work.get(5, TimeUnit.SECONDS).succeeded());
            assertEquals(0, pool.activeProcesses());
            assertEquals(WorkerFailure.CLOSED, failure(pool.execute(binding(), command("pass"))));
        } finally { pool.close(); }
    }

    @Test void backoffBlocksRestartStormsAndCancellationDoesNotLaunch() {
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ofSeconds(30)))) {
            assertEquals(WorkerFailure.CRASH, failure(pool.execute(binding(), command("crash"))));
            assertEquals(WorkerFailure.BACKOFF, failure(pool.execute(binding(), command("pass"))));
        }
        try (var pool = new WorkerPool(limits(Duration.ofSeconds(10), Duration.ZERO))) {
            Thread.currentThread().interrupt();
            try {
                assertEquals(WorkerFailure.CANCELLED, failure(pool.execute(binding(), command("pass"))));
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(0, pool.activeProcesses());
            } finally { Thread.interrupted(); }
        }
    }
}
