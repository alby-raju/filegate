package dev.filegate.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;
import java.util.Objects;

/** Scoped capability for approved bytes. Publication APIs verify bytes on every copy. */
public final class ApprovedContent {
    private final CapturedArtifact artifact;
    private final CompiledPolicy policy;
    private final int bufferBytes;

    ApprovedContent(CapturedArtifact artifact, CompiledPolicy policy, int bufferBytes) {
        this.artifact = artifact;
        this.policy = policy;
        this.bufferBytes = bufferBytes;
    }

    public ArtifactIdentity identity() { return artifact.identity; }
    public Use use() { return policy.use(); }
    public String policyFingerprint() { return policy.fingerprint(); }

    /**
     * Copy only to a private pending destination. Caller owns the destination stream;
     * this method neither flushes nor closes it. Abort on any failure. Generic streams
     * require host-provided I/O timeouts. A receipt proves streamed bytes, not persistence.
     */
    public TransferReceipt copyTo(OutputStream output, String pendingDestinationId, Use destinationUse)
            throws TransferException {
        Objects.requireNonNull(output, "output");
        Names.token(pendingDestinationId);
        Objects.requireNonNull(destinationUse, "destinationUse");
        if (destinationUse != policy.use()) {
            throw new IllegalArgumentException("Destination use differs from approved use");
        }
        try (var lease = artifact.retain(); var input = lease.open()) {
            var digest = Digests.sha256();
            byte[] buffer = new byte[bufferBytes];
            long count = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new TransferException(TransferFailure.CANCELLED);
                long remaining = artifact.identity.length() - count;
                int wanted = (int) (remaining >= buffer.length ? buffer.length : remaining + 1);
                int read = input.read(buffer, 0, wanted);
                if (read == -1) break;
                if (read > remaining) throw new TransferException(TransferFailure.INTEGRITY_MISMATCH);
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                count += read;
            }
            if (count != artifact.identity.length()
                    || !HexFormat.of().formatHex(digest.digest()).equals(artifact.identity.sha256())) {
                throw new TransferException(TransferFailure.INTEGRITY_MISMATCH);
            }
            return new TransferReceipt(artifact.identity, policy.fingerprint(), policy.use(), pendingDestinationId);
        } catch (TransferException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new TransferException(TransferFailure.IO_FAILURE);
        }
    }

    void close() { artifact.close(); }
}
