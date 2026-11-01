package dev.filegate.core;

import java.util.Optional;

final class ResourceLedger {
    private final ResourceLimits limits;
    private int operations;
    private long bytes;

    ResourceLedger(ResourceLimits limits) { this.limits = limits; }

    synchronized Optional<Reservation> reserve(long requested) {
        if (requested <= 0) {
            throw new IllegalArgumentException("Reservation must be positive");
        }
        // Subtraction keeps the admission calculation safe even near Long.MAX_VALUE.
        if (operations >= limits.maxOperations() || requested > limits.maxStagingBytes() - bytes) {
            return Optional.empty();
        }
        operations++;
        bytes += requested;
        return Optional.of(new Reservation(requested));
    }

    synchronized ResourceUsage usage(int pendingDeletions) {
        return new ResourceUsage(operations, bytes, pendingDeletions);
    }

    final class Reservation implements AutoCloseable {
        private final long reserved;
        private boolean released;

        private Reservation(long reserved) { this.reserved = reserved; }

        @Override public void close() {
            synchronized (ResourceLedger.this) {
                if (!released) {
                    released = true;
                    operations--;
                    bytes -= reserved;
                }
            }
        }
    }
}
