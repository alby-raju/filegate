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

package dev.filegate.core.internal.inspection;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.InspectionReport;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.inspection.Verdict;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.PolicySpec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class EvidenceAggregator {
    private EvidenceAggregator() {}

    public static InspectionReport aggregate(CompiledPolicy policy, List<CheckEvidence> supplied,
                                      List<ReasonCode> operationalReasons) {
        Set<String> required = policy.checks().stream().map(CheckDescriptor::id).collect(Collectors.toSet());
        var reasons = EnumSet.noneOf(ReasonCode.class);
        reasons.addAll(operationalReasons);
        Map<String, CheckEvidence> accepted = new HashMap<>();
        boolean rejected = reasons.stream().anyMatch(r -> r.status() == CheckStatus.FAIL);
        // The caller must supply bounded evidence; reject an oversized set without processing it.
        if (supplied.size() > PolicySpec.MAX_CHECKS) {
            reasons.add(ReasonCode.INVALID_EVIDENCE);
        } else {
            for (CheckEvidence item : supplied) {
                if (item == null || !required.contains(item.checkId())) {
                    reasons.add(ReasonCode.INVALID_EVIDENCE);
                    continue;
                }
                rejected |= item.status() == CheckStatus.FAIL;
                if (accepted.putIfAbsent(item.checkId(), item) != null) {
                    reasons.add(ReasonCode.INVALID_EVIDENCE);
                }
            }
        }
        List<CheckEvidence> ordered = new ArrayList<>();
        for (CheckDescriptor check : policy.checks()) {
            CheckEvidence item = accepted.get(check.id());
            if (item == null) {
                reasons.add(ReasonCode.MISSING_EVIDENCE);
                item = new CheckEvidence(check.id(), CheckStatus.NOT_RUN, ReasonCode.CHECK_NOT_RUN);
            }
            if (item.status() != CheckStatus.PASS) {
                reasons.add(item.reason());
            }
            ordered.add(item);
        }
        Verdict verdict = rejected ? Verdict.REJECTED
                : reasons.isEmpty() ? Verdict.APPROVED : Verdict.INCOMPLETE;
        return new InspectionReport(policy, verdict, ordered, List.copyOf(reasons));
    }
}
