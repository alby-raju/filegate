package dev.filegate.core;

public final class Approved implements Inspection {
    private final InspectionReport report;
    private final ApprovedContent content;

    Approved(InspectionReport report, CapturedArtifact artifact, int bufferBytes) {
        this.report = report;
        this.content = new ApprovedContent(artifact, report.policy(), bufferBytes);
    }

    @Override public InspectionReport report() { return report; }
    public ApprovedContent content() { return content; }
    @Override public void close() { content.close(); }
}
