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
            store.reclaimed(this);
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
