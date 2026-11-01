package dev.filegate.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, resolved contract. Existing operations retain this snapshot on reload. */
public final class CompiledPolicy {
    private final PolicySpec spec;
    private final List<CheckDescriptor> checks;
    private final String fingerprint;

    private CompiledPolicy(PolicySpec spec, List<CheckDescriptor> checks) {
        this.spec = spec;
        this.checks = List.copyOf(checks);
        this.fingerprint = fingerprint(spec, checks);
    }

    public static CompiledPolicy compile(PolicySpec spec, Collection<CheckDescriptor> installed) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(installed, "installed");
        Map<String, CheckDescriptor> capabilities = new HashMap<>();
        for (CheckDescriptor descriptor : installed) {
            Objects.requireNonNull(descriptor, "descriptor");
            if (capabilities.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate installed check");
            }
        }
        List<CheckDescriptor> resolved = new ArrayList<>();
        for (String id : spec.requiredChecks().stream().sorted().toList()) {
            CheckDescriptor descriptor = capabilities.get(id);
            if (descriptor == null) {
                throw new IllegalArgumentException("Mandatory check is not installed: " + id);
            }
            if (descriptor.executionMode() == ExecutionMode.ISOLATED
                    && spec.executionMode() != ExecutionMode.ISOLATED) {
                throw new IllegalArgumentException("Mandatory check requires isolation");
            }
            resolved.add(descriptor);
        }
        if (resolved.stream().noneMatch(check -> check.kind() == CheckKind.CONTENT)) {
            throw new IllegalArgumentException("Preflight cannot replace mandatory content inspection");
        }
        resolved.sort(java.util.Comparator.comparing(CheckDescriptor::kind).thenComparing(CheckDescriptor::id));
        return new CompiledPolicy(spec, resolved);
    }

    public String name() { return spec.name(); }
    public Use use() { return spec.use(); }
    public long maxInputBytes() { return spec.maxInputBytes(); }
    public ExecutionMode executionMode() { return spec.executionMode(); }
    public List<CheckDescriptor> checks() { return checks; }
    public String fingerprint() { return fingerprint; }

    private static String fingerprint(PolicySpec spec, List<CheckDescriptor> checks) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                // Versioned, length-delimited canonical encoding, independent of locale and map order.
                output.writeUTF("filegate-policy-v2");
                output.writeUTF(spec.name());
                output.writeUTF(spec.use().name());
                output.writeLong(spec.maxInputBytes());
                output.writeUTF(spec.executionMode().name());
                output.writeInt(checks.size());
                for (CheckDescriptor check : checks) {
                    output.writeUTF(check.id());
                    output.writeUTF(check.version());
                    output.writeUTF(check.coverageProfile());
                    output.writeUTF(check.executionMode().name());
                    output.writeUTF(check.kind().name());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
