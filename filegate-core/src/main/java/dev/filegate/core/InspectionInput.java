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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Scoped, read-only access for an inspector. At most one stream may be open at a time. */
public final class InspectionInput implements AutoCloseable {
    private final CapturedArtifact.Lease lease;
    private final ArtifactIdentity identity;
    private final InputClaims claims;
    private InputStream active;
    private boolean closed;

    InspectionInput(CapturedArtifact artifact, InputClaims claims) {
        this.lease = artifact.retain();
        this.identity = artifact.identity;
        this.claims = claims;
    }

    public ArtifactIdentity identity() { return identity; }
    public InputClaims claims() { return claims; }

    public synchronized InputStream openStream() throws IOException {
        if (closed) throw new IllegalStateException("Inspector input is closed");
        if (active != null) throw new IllegalStateException("Close the previous inspector stream first");
        active = new FilterInputStream(lease.open()) {
            private boolean streamClosed;
            @Override public void close() throws IOException {
                synchronized (InspectionInput.this) {
                    if (!streamClosed) {
                        streamClosed = true;
                        try { super.close(); } finally { active = null; }
                    }
                }
            }
        };
        return active;
    }

    @Override public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                if (active != null) active.close();
            } finally {
                lease.close();
            }
        }
    }
}
