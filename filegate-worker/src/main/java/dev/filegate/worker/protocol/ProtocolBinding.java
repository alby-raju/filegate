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

package dev.filegate.worker.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.filegate.core.content.ArtifactIdentity;

/** Every response must match all fields, including the exact expected check set. */
public record ProtocolBinding(UUID requestId, ArtifactIdentity artifact, String policyFingerprint,
                              String inspectorBuild, List<String> expectedChecks) {
    public ProtocolBinding {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(artifact, "artifact");
        if (policyFingerprint == null || !policyFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid policy fingerprint");
        }
        token(inspectorBuild);
        if (expectedChecks.isEmpty() || expectedChecks.size() > WorkerProtocol.MAX_CHECKS) {
            throw new IllegalArgumentException("Invalid expected check count");
        }
        expectedChecks = expectedChecks.stream().map(ProtocolBinding::token).sorted().toList();
        if (expectedChecks.stream().distinct().count() != expectedChecks.size()) {
            throw new IllegalArgumentException("Duplicate expected check");
        }
    }

    static String token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("Invalid bounded protocol identifier");
        }
        return value;
    }
}
