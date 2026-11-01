package dev.filegate.core;

/** A blocking result exposes evidence, never an approved-content handle. */
public final class Blocked implements Inspection {
    private final InspectionReport report;

    Blocked(InspectionReport report) { this.report = report; }
    @Override public InspectionReport report() { return report; }
    @Override public void close() {}
}
