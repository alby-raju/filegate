package dev.filegate.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Package-private stage ownership. Paths never cross the public API. */
final class CapturedArtifact implements AutoCloseable {
    final Path path;
    final ArtifactIdentity identity;
    private final StageStore store;
    private final ResourceLedger.Reservation reservation;
    private int readers;
    private boolean ownerClosed;
    private boolean deleted;

    CapturedArtifact(Path path, ArtifactIdentity identity, StageStore store,
                     ResourceLedger.Reservation reservation) {
        this.path = path;
        this.identity = identity;
        this.store = store;
        this.reservation = reservation;
    }

    synchronized Lease retain() {
        if (ownerClosed) {
            throw new IllegalStateException("Inspection is closed");
        }
        readers++;
        return new Lease();
    }

    @Override public synchronized void close() {
        ownerClosed = true;
        tryDelete();
    }

    synchronized boolean tryDelete() {
        if (!deleted && ownerClosed && readers == 0 && store.delete(path)) {
            deleted = true;
            reservation.close();
        }
        return deleted;
    }

    synchronized boolean pendingDeletion() { return ownerClosed && readers == 0 && !deleted; }

    final class Lease implements AutoCloseable {
        private boolean closed;

        synchronized InputStream open() throws IOException {
            if (closed) {
                throw new IllegalStateException("Lease is closed");
            }
            return Files.newInputStream(path);
        }

        @Override public void close() {
            synchronized (CapturedArtifact.this) {
                synchronized (this) {
                    if (closed) return;
                    closed = true;
                }
                readers--;
                tryDelete();
            }
        }
    }
}
