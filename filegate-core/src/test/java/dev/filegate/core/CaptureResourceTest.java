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

package dev.filegate.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CaptureResourceTest {
    @TempDir Path temp;

    @Test void exactLimitAndEmptyCaptureBindLengthDigestAndPrivatePermissions() throws Exception {
        try (var store = new StageStore(temp, new ResourceLimits(2, 32, 1024))) {
            for (byte[] bytes : new byte[][] {new byte[0], new byte[16]}) {
                var capture = store.capture(new ByteArrayInputStream(bytes), 16);
                assertNull(capture.failure());
                var artifact = capture.artifact();
                assertEquals(bytes.length, artifact.identity.length());
                assertEquals(HexFormat.of().formatHex(Digests.sha256().digest(bytes)), artifact.identity.sha256());
                assertEquals(PosixFilePermissions.fromString("r--------"), Files.getPosixFilePermissions(artifact.path));
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(artifact.path.getParent()));
                artifact.close();
                assertFalse(Files.exists(artifact.path));
                assertEquals(0, store.usage().reservedStagingBytes());
            }
        }
        try (var files = Files.list(temp)) { assertEquals(0, files.count()); }
    }

    @Test void overLimitReadsOnlyOneExtraByteAndNeverRetainsRejectedInput() throws Exception {
        var source = new ByteArrayInputStream(new byte[100]);
        try (var store = new StageStore(temp, new ResourceLimits(1, 10, 1024))) {
            var capture = store.capture(source, 10);
            assertEquals(ReasonCode.INPUT_TOO_LARGE, capture.failure());
            assertNull(capture.artifact());
            assertEquals(89, source.available());
            assertEquals(0, store.usage().operations());
        }
    }

    @Test void capacityDenialHappensBeforeSourceReadAndSourceRemainsCallerOwned() throws Exception {
        var closed = new AtomicBoolean();
        var source = new ByteArrayInputStream(new byte[1]) {
            @Override public void close() { closed.set(true); }
        };
        try (var store = new StageStore(temp, new ResourceLimits(1, 10, 1024))) {
            var first = store.capture(source, 10).artifact();
            assertFalse(closed.get());
            var denied = store.capture(new InputStream() {
                @Override public int read() { fail("Capacity denial must not read input"); return -1; }
            }, 10);
            assertEquals(ReasonCode.CAPACITY_EXHAUSTED, denied.failure());
            first.close();
            assertFalse(closed.get());
        }
    }

    @Test void failedReadCleansStageAndReleasesQuota() throws Exception {
        try (var store = new StageStore(temp, new ResourceLimits(1, 10, 1024))) {
            var capture = store.capture(new InputStream() {
                @Override public int read() throws IOException { throw new IOException("private details"); }
            }, 10);
            assertEquals(ReasonCode.CAPTURE_FAILED, capture.failure());
            assertEquals(0, store.usage().reservedStagingBytes());
            assertEquals(0, store.usage().pendingDeletions());
        }
    }

    @Test void deletionFailureKeepsReservationUntilRetrySucceeds() throws Exception {
        var failDeletion = new AtomicBoolean(true);
        try (var store = new StageStore(temp, new ResourceLimits(1, 10, 1024), path -> {
            if (failDeletion.get()) throw new IOException("injected deletion failure");
            Files.deleteIfExists(path);
        })) {
            var artifact = store.capture(new ByteArrayInputStream(new byte[1]), 10).artifact();
            artifact.close();
            artifact.close();
            assertEquals(new ResourceUsage(1, 10, 1), store.usage());
            assertEquals(ReasonCode.CAPACITY_EXHAUSTED,
                    store.capture(new ByteArrayInputStream(new byte[1]), 10).failure());
            failDeletion.set(false);
            store.retryCleanup();
            assertEquals(new ResourceUsage(0, 0, 0), store.usage());
            assertFalse(Files.exists(artifact.path));
        }
    }

    @Test void failedCaptureAlsoRetainsQuotaWhenDeletionFails() throws Exception {
        var failDeletion = new AtomicBoolean(true);
        try (var store = new StageStore(temp, new ResourceLimits(1, 10, 1024), path -> {
            if (failDeletion.get()) throw new IOException("injected");
            Files.deleteIfExists(path);
        })) {
            assertEquals(ReasonCode.INPUT_TOO_LARGE,
                    store.capture(new ByteArrayInputStream(new byte[11]), 10).failure());
            assertEquals(new ResourceUsage(1, 10, 1), store.usage());
            failDeletion.set(false);
            store.retryCleanup();
            assertEquals(0, store.usage().reservedStagingBytes());
        }
    }

    @Test void quotaArithmeticCannotOverflowAndReleaseIsIdempotent() {
        var ledger = new ResourceLedger(new ResourceLimits(2, Long.MAX_VALUE, 1024));
        var lease = ledger.reserve(Long.MAX_VALUE).orElseThrow();
        assertTrue(ledger.reserve(1).isEmpty());
        lease.close();
        lease.close();
        assertEquals(0, ledger.usage(0).operations());
        assertTrue(ledger.reserve(Long.MAX_VALUE).isPresent());
    }

    @Test void concurrentReservationsNeverOvercommit() throws Exception {
        var ledger = new ResourceLedger(new ResourceLimits(3, 30, 1024));
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(16)) {
            var attempts = new ArrayList<java.util.concurrent.Future<java.util.Optional<ResourceLedger.Reservation>>>();
            for (int i = 0; i < 16; i++) attempts.add(pool.submit(() -> { go.await(); return ledger.reserve(10); }));
            go.countDown();
            var admitted = new ArrayList<ResourceLedger.Reservation>();
            for (var attempt : attempts) attempt.get().ifPresent(admitted::add);
            assertEquals(3, admitted.size());
            assertEquals(30, ledger.usage(0).reservedStagingBytes());
            admitted.forEach(ResourceLedger.Reservation::close);
            assertEquals(0, ledger.usage(0).operations());
        }
    }
}
