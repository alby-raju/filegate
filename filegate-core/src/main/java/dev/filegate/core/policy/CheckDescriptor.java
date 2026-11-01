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

package dev.filegate.core.policy;

import java.util.Objects;

import dev.filegate.core.internal.validation.Identifiers;

/** Explicit capability identity. Version identifies the installed inspector build. */
public record CheckDescriptor(String id, String version, String coverageProfile,
                              ExecutionMode executionMode, CheckKind kind) {
    public CheckDescriptor(String id, String version, String coverageProfile, ExecutionMode executionMode) {
        this(id, version, coverageProfile, executionMode, CheckKind.CONTENT);
    }

    public CheckDescriptor {
        id = Identifiers.requireToken(id);
        version = Identifiers.requireToken(version);
        coverageProfile = Identifiers.requireToken(coverageProfile);
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(kind, "kind");
    }
}
