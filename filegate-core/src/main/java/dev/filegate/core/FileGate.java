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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** One long-lived gate per resource domain. This initial executor supports EMBEDDED only. */
public final class FileGate implements AutoCloseable {
    private final StageStore stages;
    private final ResourceLimits limits;
    private final Map<String, InspectionCheck> checks;
    private final AtomicReference<CompiledPolicy> policy;

    private FileGate(StageStore stages, ResourceLimits limits, CompiledPolicy policy,
                     Map<String, InspectionCheck> checks) {
        this.stages = stages;
        this.limits = limits;
        this.checks = Map.copyOf(checks);
        this.policy = new AtomicReference<>(policy);
    }

    public static FileGate open(Path stagingParent, ResourceLimits limits, CompiledPolicy policy,
                                List<InspectionCheck> installed) throws IOException {
        Objects.requireNonNull(limits, "limits");
        Map<String, InspectionCheck> checks = new HashMap<>();
        for (InspectionCheck check : installed) {
            Objects.requireNonNull(check, "check");
            if (checks.putIfAbsent(check.descriptor().id(), check) != null) {
                throw new IllegalArgumentException("Duplicate installed check");
            }
        }
        validate(policy, limits, checks);
        return new FileGate(new StageStore(stagingParent, limits), limits, policy, checks);
    }

    /** Existing operations and reports retain their resolved policy snapshot. */
    public void reload(CompiledPolicy next) {
        validate(next, limits, checks);
        policy.set(next);
    }

    public Inspection inspect(InputStream source, InputClaims claims) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(claims, "claims");
        CompiledPolicy snapshot = policy.get();
        PreflightCheck preflight = snapshot.checks().stream().map(check -> checks.get(check.id()))
                .filter(PreflightCheck.class::isInstance).map(PreflightCheck.class::cast).findFirst().orElse(null);
        var capture = stages.capture(source, snapshot.maxInputBytes(), claims, preflight);
        if (capture.failure() != null) {
            return new Blocked(EvidenceAggregator.aggregate(snapshot,
                    capture.preflightEvidence() == null ? List.of() : List.of(capture.preflightEvidence()),
                    List.of(capture.failure())));
        }
        CapturedArtifact artifact = capture.artifact();
        boolean transferred = false;
        try {
            var evidence = new ArrayList<CheckEvidence>();
            for (CheckDescriptor descriptor : snapshot.checks()) {
                if (capture.preflightEvidence() != null && descriptor.id().equals(capture.preflightEvidence().checkId())) {
                    evidence.add(capture.preflightEvidence());
                    continue;
                }
                CheckEvidence result;
                try (var input = new InspectionInput(artifact, claims)) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    result = checks.get(descriptor.id()).inspect(input);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    result = new CheckEvidence(descriptor.id(), CheckStatus.ERROR, ReasonCode.CANCELLED);
                } catch (Exception failure) {
                    result = new CheckEvidence(descriptor.id(), CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
                }
                evidence.add(result);
                if (result == null || !descriptor.id().equals(result.checkId()) || result.status() != CheckStatus.PASS) {
                    break;
                }
            }
            InspectionReport report = EvidenceAggregator.aggregate(snapshot, evidence, List.of())
                    .withArtifact(artifact.identity);
            if (report.verdict() == Verdict.APPROVED) {
                var approved = new Approved(report, artifact, limits.captureBufferBytes());
                transferred = true;
                return approved;
            }
            return new Blocked(report);
        } finally {
            if (!transferred) artifact.close();
        }
    }

    /** Path sources are always copied into owned staging; caller files are never modified. */
    public Inspection inspect(Path source, InputClaims claims) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(claims, "claims");
        Inspection inspection = null;
        try (var input = Files.newInputStream(source)) {
            inspection = inspect(input, claims);
        } catch (IOException failure) {
            if (inspection != null) inspection.close();
            return new Blocked(EvidenceAggregator.aggregate(policy.get(), List.of(), List.of(ReasonCode.CAPTURE_FAILED)));
        }
        return inspection;
    }

    public ResourceUsage resourceUsage() { return stages.usage(); }
    public void retryCleanup() { stages.retryCleanup(); }

    /** Stops new capture. Existing results and in-flight copies remain caller-owned. */
    @Override public void close() { stages.close(); }

    private static void validate(CompiledPolicy policy, ResourceLimits limits, Map<String, InspectionCheck> checks) {
        Objects.requireNonNull(policy, "policy");
        if (policy.executionMode() != ExecutionMode.EMBEDDED) {
            throw new IllegalArgumentException("No isolated executor is installed; embedded fallback is forbidden");
        }
        if (policy.maxInputBytes() > limits.maxStagingBytes()) {
            throw new IllegalArgumentException("Policy input allowance exceeds the stage envelope");
        }
        for (CheckDescriptor required : policy.checks()) {
            InspectionCheck installed = checks.get(required.id());
            if (installed == null || !required.equals(installed.descriptor())) {
                throw new IllegalArgumentException("Installed check does not match compiled capability");
            }
        }
    }
}
