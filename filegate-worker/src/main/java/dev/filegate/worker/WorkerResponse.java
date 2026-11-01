package dev.filegate.worker;

import dev.filegate.core.CheckEvidence;
import dev.filegate.core.PolicySpec;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record WorkerResponse(ProtocolBinding binding, List<CheckEvidence> evidence) {
    public WorkerResponse {
        Objects.requireNonNull(binding, "binding");
        if (evidence.size() > PolicySpec.MAX_CHECKS) throw new IllegalArgumentException("Too many findings");
        evidence = List.copyOf(evidence);
    }

    public void validateFor(ProtocolBinding expected) throws IOException {
        if (!binding.equals(expected) || evidence.size() != expected.expectedChecks().size()) {
            throw new IOException("Worker response binding mismatch");
        }
        var found = new HashSet<String>();
        for (CheckEvidence result : evidence) {
            if (!expected.expectedChecks().contains(result.checkId()) || !found.add(result.checkId())) {
                throw new IOException("Unexpected or duplicate worker evidence");
            }
        }
    }
}
