/*
 * FileGate
 * Policy-based attachment inspection and verified release.
 *
 * Copyright 2026 alby-raju
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.filegate.core;

import java.util.List;
import java.util.Optional;

/** Completed checks and host obligations are separate; no host obligation is a PASS. */
public final class InspectionReport {
    private final CompiledPolicy policy;
    private final Verdict verdict;
    private final List<CheckEvidence> evidence;
    private final List<ReasonCode> reasons;
    private final ArtifactIdentity artifact;

    InspectionReport(CompiledPolicy policy, Verdict verdict,
                     List<CheckEvidence> evidence, List<ReasonCode> reasons) {
        this(policy, verdict, evidence, reasons, null);
    }

    private InspectionReport(CompiledPolicy policy, Verdict verdict,
                     List<CheckEvidence> evidence, List<ReasonCode> reasons, ArtifactIdentity artifact) {
        this.policy = policy;
        this.verdict = verdict;
        this.evidence = List.copyOf(evidence);
        this.reasons = List.copyOf(reasons);
        this.artifact = artifact;
    }

    public CompiledPolicy policy() { return policy; }
    public Verdict verdict() { return verdict; }
    public List<CheckEvidence> evidence() { return evidence; }
    public List<ReasonCode> reasons() { return reasons; }
    public Optional<ArtifactIdentity> artifact() { return Optional.ofNullable(artifact); }

    InspectionReport withArtifact(ArtifactIdentity identity) {
        return new InspectionReport(policy, verdict, evidence, reasons, identity);
    }
    public List<String> hostObligations() {
        return List.of("Authenticate and authorize access", "Enforce upload and transport timeouts",
                "Write privately and abort unless finalized and committed",
                "Enforce destination use, serving controls and retention");
    }
}
