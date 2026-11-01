package dev.filegate.core;

import java.util.UUID;

/** Scoped transfer evidence, not a portable authorization or persistence token. */
public final class TransferReceipt {
    private final String transferId = UUID.randomUUID().toString();
    private final ArtifactIdentity artifact;
    private final String policyFingerprint;
    private final Use use;
    private final String pendingDestinationId;

    TransferReceipt(ArtifactIdentity artifact, String policyFingerprint, Use use, String pendingDestinationId) {
        this.artifact = artifact;
        this.policyFingerprint = policyFingerprint;
        this.use = use;
        this.pendingDestinationId = pendingDestinationId;
    }

    public String transferId() { return transferId; }
    public ArtifactIdentity artifact() { return artifact; }
    public String policyFingerprint() { return policyFingerprint; }
    public Use use() { return use; }
    public String pendingDestinationId() { return pendingDestinationId; }

    /** The host must additionally finalize writes, enforce authorization and commit visibility. */
    public void validateFor(String destinationId, Use destinationUse) {
        if (!pendingDestinationId.equals(destinationId) || use != destinationUse) {
            throw new IllegalArgumentException("Receipt does not belong to destination and use");
        }
    }
}
