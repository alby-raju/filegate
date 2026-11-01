package dev.filegate.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class VerifiedTransferTest {
    @TempDir Path temp;

    @Test void exactBytesReceiptBindingAndCallerStreamOwnership() throws Exception {
        try (var store = new StageStore(temp, new ResourceLimits(1, 32, 1024))) {
            var artifact = store.capture(new ByteArrayInputStream(new byte[] {1, 2, 3}), 32).artifact();
            var content = new ApprovedContent(artifact, PolicyEvidenceTest.policy(32, "a"), 1024);
            var closed = new AtomicBoolean();
            var flushed = new AtomicBoolean();
            var output = new ByteArrayOutputStream() {
                @Override public void close() { closed.set(true); }
                @Override public void flush() { flushed.set(true); }
            };
            var receipt = content.copyTo(output, "pending-1", Use.DOWNLOAD);
            assertArrayEquals(new byte[] {1, 2, 3}, output.toByteArray());
            assertEquals(content.identity(), receipt.artifact());
            assertEquals(content.policyFingerprint(), receipt.policyFingerprint());
            receipt.validateFor("pending-1", Use.DOWNLOAD);
            assertThrows(IllegalArgumentException.class, () -> receipt.validateFor("pending-2", Use.DOWNLOAD));
            assertThrows(IllegalArgumentException.class, () -> receipt.validateFor("pending-1", Use.EXTERNAL_SHARE));
            assertFalse(closed.get());
            assertFalse(flushed.get());
            content.close();
            assertThrows(IllegalStateException.class, () -> content.copyTo(output, "pending-1", Use.DOWNLOAD));
        }
    }

    @Test void wrongUseFailsBeforeWriting() throws Exception {
        try (var store = new StageStore(temp, new ResourceLimits(1, 32, 1024))) {
            var artifact = store.capture(new ByteArrayInputStream(new byte[1]), 32).artifact();
            var content = new ApprovedContent(artifact, PolicyEvidenceTest.policy(32, "a"), 1024);
            assertThrows(IllegalArgumentException.class, () -> content.copyTo(new OutputStream() {
                @Override public void write(int b) { fail("Wrong-use transfer must not write"); }
            }, "pending", Use.INLINE_PREVIEW));
            content.close();
        }
    }

    @Test void changedTruncatedAndExtendedStagesCannotProduceReceipt() throws Exception {
        for (byte[] replacement : new byte[][] {new byte[] {9, 9}, new byte[] {1}, new byte[] {1, 2, 3}}) {
            try (var store = new StageStore(temp, new ResourceLimits(1, 32, 1024))) {
                var artifact = store.capture(new ByteArrayInputStream(new byte[] {1, 2}), 32).artifact();
                var content = new ApprovedContent(artifact, PolicyEvidenceTest.policy(32, "a"), 1024);
                // Fault injection simulates violation of host-owned staging; no path is public.
                Files.setPosixFilePermissions(artifact.path, PosixFilePermissions.fromString("rw-------"));
                Files.write(artifact.path, replacement);
                var failure = assertThrows(TransferException.class,
                        () -> content.copyTo(new ByteArrayOutputStream(), "pending", Use.DOWNLOAD));
                assertEquals(TransferFailure.INTEGRITY_MISMATCH, failure.failure());
                content.close();
            }
        }
    }

    @Test void destinationFailureReturnsNoReceiptAndReleasesCopyLease() throws Exception {
        try (var store = new StageStore(temp, new ResourceLimits(1, 32, 1024))) {
            var artifact = store.capture(new ByteArrayInputStream(new byte[3]), 32).artifact();
            var content = new ApprovedContent(artifact, PolicyEvidenceTest.policy(32, "a"), 1024);
            var failure = assertThrows(TransferException.class, () -> content.copyTo(new OutputStream() {
                @Override public void write(int b) throws IOException { throw new IOException("secret destination details"); }
            }, "pending", Use.DOWNLOAD));
            assertEquals(TransferFailure.IO_FAILURE, failure.failure());
            assertFalse(failure.getMessage().contains("secret"));
            content.close();
            assertEquals(0, store.usage().operations());
        }
    }

    @Test void activeCopyCompletesAfterResultCloseButNewCopiesFail() throws Exception {
        var writing = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        try (var store = new StageStore(temp, new ResourceLimits(1, 32, 1024));
             var pool = Executors.newSingleThreadExecutor()) {
            var artifact = store.capture(new ByteArrayInputStream(new byte[] {1}), 32).artifact();
            var content = new ApprovedContent(artifact, PolicyEvidenceTest.policy(32, "a"), 1024);
            var copy = pool.submit(() -> content.copyTo(new OutputStream() {
                @Override public void write(int b) throws IOException {
                    writing.countDown();
                    try {
                        if (!proceed.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout");
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(); }
                }
            }, "pending", Use.DOWNLOAD));
            try {
                assertTrue(writing.await(5, TimeUnit.SECONDS));
                content.close();
                assertEquals(32, store.usage().reservedStagingBytes());
                assertThrows(IllegalStateException.class,
                        () -> content.copyTo(new ByteArrayOutputStream(), "other", Use.DOWNLOAD));
            } finally { proceed.countDown(); }
            assertEquals(1, copy.get(5, TimeUnit.SECONDS).artifact().length());
            assertEquals(0, store.usage().reservedStagingBytes());
        } finally { proceed.countDown(); }
    }
}
