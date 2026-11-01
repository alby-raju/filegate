package dev.filegate.core;

/** Explicit per-gate envelope. Shared volumes need externally partitioned quotas. */
public record ResourceLimits(int maxOperations, long maxStagingBytes, int captureBufferBytes) {
    public ResourceLimits {
        if (maxOperations <= 0 || maxStagingBytes <= 0
                || captureBufferBytes < 1024 || captureBufferBytes > 1024 * 1024) {
            throw new IllegalArgumentException("Invalid operation, stage or buffer limit");
        }
    }
}
