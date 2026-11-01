package dev.filegate.core;

import java.util.Objects;
import java.util.Set;

public record PreflightRules(Set<FormatCandidate> allowedCandidates, ClaimPolicy claims) {
    public PreflightRules {
        allowedCandidates = Set.copyOf(allowedCandidates);
        Objects.requireNonNull(claims, "claims");
        if (allowedCandidates.isEmpty() || allowedCandidates.contains(FormatCandidate.UNKNOWN)) {
            throw new IllegalArgumentException("Explicit supported signature candidates are required");
        }
    }
}
