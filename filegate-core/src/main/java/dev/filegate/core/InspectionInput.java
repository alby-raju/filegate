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
