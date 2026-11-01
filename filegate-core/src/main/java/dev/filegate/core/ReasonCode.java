package dev.filegate.core;

/** Stable, bounded diagnostics. No filenames, removed content or parser messages. */
public enum ReasonCode {
    CHECK_PASSED(CheckStatus.PASS),
    POLICY_VIOLATION(CheckStatus.FAIL),
    INPUT_TOO_LARGE(CheckStatus.FAIL),
    UNSUPPORTED_FORMAT(CheckStatus.FAIL),
    CLAIM_MISMATCH(CheckStatus.FAIL),
    CHECK_ERROR(CheckStatus.ERROR),
    CAPACITY_EXHAUSTED(CheckStatus.ERROR),
    CAPTURE_FAILED(CheckStatus.ERROR),
    CLEANUP_PENDING(CheckStatus.ERROR),
    INVALID_EVIDENCE(CheckStatus.ERROR),
    MISSING_EVIDENCE(CheckStatus.ERROR),
    CHECK_NOT_RUN(CheckStatus.NOT_RUN);

    private final CheckStatus status;

    ReasonCode(CheckStatus status) {
        this.status = status;
    }

    public CheckStatus status() {
        return status;
    }
}
