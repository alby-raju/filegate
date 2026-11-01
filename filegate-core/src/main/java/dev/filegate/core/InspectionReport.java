package dev.filegate.core;

import java.util.List;

/** Completed checks and host obligations are separate; no host obligation is a PASS. */
public final class InspectionReport {
    private final CompiledPolicy policy;
    private final Verdict verdict;
    private final List<CheckEvidence> evidence;
    private final List<ReasonCode> reasons;

    InspectionReport(CompiledPolicy policy, Verdict verdict,
                     List<CheckEvidence> evidence, List<ReasonCode> reasons) {
        this.policy = policy;
        this.verdict = verdict;
        this.evidence = List.copyOf(evidence);
        this.reasons = List.copyOf(reasons);
    }

    public CompiledPolicy policy() { return policy; }
    public Verdict verdict() { return verdict; }
    public List<CheckEvidence> evidence() { return evidence; }
    public List<ReasonCode> reasons() { return reasons; }
    public List<String> hostObligations() {
        return List.of("Authenticate and authorize access", "Enforce upload and transport timeouts",
                "Write privately and abort unless finalized and committed",
                "Enforce destination use, serving controls and retention");
    }
}
