package dev.filegate.worker;

/** Exactly one response or blocking failure. A successful response is not a content verdict. */
public record WorkerRun(WorkerResponse response, WorkerFailure failure) {
    public WorkerRun {
        if ((response == null) == (failure == null)) throw new IllegalArgumentException("Expected response or failure");
    }

    public boolean succeeded() { return response != null; }
}
