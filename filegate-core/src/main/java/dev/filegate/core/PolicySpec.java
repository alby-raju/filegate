package dev.filegate.core;

import java.util.List;
import java.util.Objects;

/** Typed configuration; there is deliberately no permissive string-key parser. */
public record PolicySpec(String name, Use use, long maxInputBytes,
                         ExecutionMode executionMode, List<String> requiredChecks) {
    public static final int MAX_CHECKS = 64;

    public PolicySpec {
        name = Names.token(name);
        Objects.requireNonNull(use, "use");
        Objects.requireNonNull(executionMode, "executionMode");
        if (maxInputBytes <= 0) {
            throw new IllegalArgumentException("maxInputBytes must be positive");
        }
        Objects.requireNonNull(requiredChecks, "requiredChecks");
        if (requiredChecks.isEmpty() || requiredChecks.size() > MAX_CHECKS) {
            throw new IllegalArgumentException("A policy requires 1 to 64 explicit checks");
        }
        requiredChecks = List.copyOf(requiredChecks);
        requiredChecks.forEach(Names::token);
        if (requiredChecks.stream().distinct().count() != requiredChecks.size()) {
            throw new IllegalArgumentException("Duplicate required check");
        }
    }
}
