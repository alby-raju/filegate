package dev.filegate.core;

import java.util.Objects;

/** One bounded check result. Free-form parser diagnostics are deliberately excluded. */
public record CheckEvidence(String checkId, CheckStatus status, ReasonCode reason) {
    public CheckEvidence {
        checkId = Names.token(checkId);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        if (reason.status() != status) {
            throw new IllegalArgumentException("Reason contradicts check status");
        }
    }

    public static CheckEvidence pass(String id) {
        return new CheckEvidence(id, CheckStatus.PASS, ReasonCode.CHECK_PASSED);
    }
}
