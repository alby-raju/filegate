package dev.filegate.core;

import java.io.IOException;

/** A failed copy grants no receipt. The host must abort its private destination. */
public final class TransferException extends IOException {
    private static final long serialVersionUID = 1L;
    private final TransferFailure failure;

    TransferException(TransferFailure failure) {
        // Do not leak content paths or downstream exception messages.
        super(failure.name());
        this.failure = failure;
    }

    public TransferFailure failure() { return failure; }
}
