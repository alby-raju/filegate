package dev.filegate.core;

/** Caller-owned result. Always close it, including after failed publication. */
public sealed interface Inspection extends AutoCloseable permits Approved, Blocked {
    InspectionReport report();
    @Override void close();
}
