package dev.filegate.worker;

import dev.filegate.core.ArtifactIdentity;
import dev.filegate.core.PolicySpec;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Every response must match all fields, including the exact expected check set. */
public record ProtocolBinding(UUID requestId, ArtifactIdentity artifact, String policyFingerprint,
                              String inspectorBuild, List<String> expectedChecks) {
    public ProtocolBinding {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(artifact, "artifact");
        if (policyFingerprint == null || !policyFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid policy fingerprint");
        }
        token(inspectorBuild);
        if (expectedChecks.isEmpty() || expectedChecks.size() > PolicySpec.MAX_CHECKS) {
            throw new IllegalArgumentException("Invalid expected check count");
        }
        expectedChecks = expectedChecks.stream().map(ProtocolBinding::token).sorted().toList();
        if (expectedChecks.stream().distinct().count() != expectedChecks.size()) {
            throw new IllegalArgumentException("Duplicate expected check");
        }
    }

    static String token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("Invalid bounded protocol identifier");
        }
        return value;
    }
}
