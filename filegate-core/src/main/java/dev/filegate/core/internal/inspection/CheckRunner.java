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
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.internal.capture.CapturedArtifact;
import dev.filegate.core.internal.execution.CheckInvocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Sequential execution owns scoped inspector access; evidence aggregation is separate. */
final class CheckRunner {
    List<CheckEvidence> run(
            CheckRegistry.Plan plan,
            CapturedArtifact artifact,
            InputClaims claims,
            List<CheckEvidence> capturedEvidence) {
        var evidence = new ArrayList<>(capturedEvidence);
        Set<String> completed =
                capturedEvidence.stream().map(CheckEvidence::checkId).collect(Collectors.toSet());
        for (CheckRegistry.RegisteredCheck check : plan.checks()) {
            String id = check.descriptor().id();
            if (completed.contains(id)) continue;
            CheckEvidence result =
                    CheckInvocation.invoke(
                            id,
                            () -> {
                                try (var input = new ScopedInspectionInput(artifact, claims)) {
                                    return check.implementation().inspect(input);
                                }
                            });
            evidence.add(result);
            if (result.status() != CheckStatus.PASS) break;
        }
        return evidence;
    }
}
