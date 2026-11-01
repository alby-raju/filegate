package dev.filegate.worker;

import java.time.Duration;

public record WorkerLimits(int maxTasks, Duration taskTimeout, Duration terminationGrace,
                           Duration failureBackoff, int maxStderrBytes) {
    public WorkerLimits {
        if (maxTasks < 1 || maxTasks > 128 || taskTimeout.isNegative() || taskTimeout.isZero()
                || taskTimeout.compareTo(Duration.ofMinutes(5)) > 0
                || terminationGrace.isNegative() || terminationGrace.isZero()
                || terminationGrace.compareTo(Duration.ofSeconds(5)) > 0
                || failureBackoff.isNegative() || failureBackoff.compareTo(Duration.ofMinutes(1)) > 0
                || maxStderrBytes < 0 || maxStderrBytes > 1024 * 1024) {
            throw new IllegalArgumentException("Invalid worker resource envelope");
        }
    }
}
