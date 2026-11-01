package dev.filegate.core;

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

    void close() { artifact.close(); }
}
