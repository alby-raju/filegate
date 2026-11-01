package dev.filegate.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class StageStore implements AutoCloseable {
    @FunctionalInterface interface Deleter { void delete(Path path) throws IOException; }
    record Capture(CapturedArtifact artifact, ReasonCode failure) {}

    private final Path root;
    private final ResourceLimits limits;
    private final ResourceLedger ledger;
    private final Deleter deleter;
    private final Set<CapturedArtifact> artifacts = ConcurrentHashMap.newKeySet();
    private boolean closed;
    private int captures;

    StageStore(Path parent, ResourceLimits limits) throws IOException {
        this(parent, limits, Files::deleteIfExists);
    }

    StageStore(Path parent, ResourceLimits limits, Deleter deleter) throws IOException {
        this.limits = limits;
        this.ledger = new ResourceLedger(limits);
        this.deleter = deleter;
        // Unsupported permissions fail construction; there is no world-readable fallback.
        this.root = Files.createTempDirectory(parent, "filegate-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    Capture capture(InputStream source, long maxBytes) {
        ResourceLedger.Reservation reservation;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Gate is closed");
            var admitted = ledger.reserve(maxBytes);
            if (admitted.isEmpty()) return new Capture(null, ReasonCode.CAPACITY_EXHAUSTED);
            reservation = admitted.get();
            captures++;
        }
        Path path = null;
        CapturedArtifact captured = null;
        ReasonCode failure = null;
        try {
            path = Files.createTempFile(root, "artifact-", ".stage",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            var digest = Digests.sha256();
            long count = 0;
            byte[] buffer = new byte[limits.captureBufferBytes()];
            try (var output = Files.newOutputStream(path)) {
                while (true) {
                    // Read at most one byte beyond the policy limit, without overflow.
                    int wanted = (int) Math.min(buffer.length,
                            maxBytes - count >= buffer.length ? buffer.length : maxBytes - count + 1);
                    int read = source.read(buffer, 0, wanted);
                    if (read == -1) break;
                    if (read == 0) {
                        // Defensive progress for broken streams; host still owns transport timeouts.
                        int next = source.read();
                        if (next == -1) break;
                        buffer[0] = (byte) next;
                        read = 1;
                    }
                    if (read > maxBytes - count) {
                        failure = ReasonCode.INPUT_TOO_LARGE;
                        break;
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    count += read;
                }
            }
            if (failure == null) {
                // Close capture writer, then remove write permission before any inspector runs.
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("r--------"));
                captured = new CapturedArtifact(path,
                        new ArtifactIdentity(count, HexFormat.of().formatHex(digest.digest())), this, reservation);
                artifacts.add(captured);
            }
        } catch (IOException | RuntimeException e) {
            if (failure == null) failure = ReasonCode.CAPTURE_FAILED;
        } finally {
            if (captured == null) {
                if (path == null) {
                    reservation.close();
                } else {
                    var discarded = new CapturedArtifact(path, null, this, reservation);
                    artifacts.add(discarded);
                    discarded.close();
                }
            }
            synchronized (this) { captures--; }
            removeDeletedAndMaybeRoot();
        }
        return new Capture(captured, failure);
    }

    boolean delete(Path path) {
        try {
            deleter.delete(path);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    void retryCleanup() {
        for (CapturedArtifact artifact : artifacts) {
            if (artifact.tryDelete()) artifacts.remove(artifact);
        }
        removeDeletedAndMaybeRoot();
    }

    ResourceUsage usage() {
        return ledger.usage((int) artifacts.stream().filter(CapturedArtifact::pendingDeletion).count());
    }

    private void removeDeletedAndMaybeRoot() {
        artifacts.removeIf(CapturedArtifact::tryDelete);
        synchronized (this) {
            if (closed && captures == 0 && artifacts.isEmpty()) delete(root);
        }
    }

    @Override public void close() {
        synchronized (this) { closed = true; }
        retryCleanup();
    }
}
