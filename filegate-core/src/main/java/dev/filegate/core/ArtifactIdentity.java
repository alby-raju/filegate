package dev.filegate.core;

/** Exact captured content identity. Avoid including this in ordinary application logs. */
public record ArtifactIdentity(long length, String sha256) {
    public ArtifactIdentity {
        if (length < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid content identity");
        }
    }

    @Override public String toString() { return "ArtifactIdentity[length=" + length + "]"; }
}
