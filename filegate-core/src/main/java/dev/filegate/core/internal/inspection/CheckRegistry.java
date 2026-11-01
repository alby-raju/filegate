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

import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CheckKind;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.spi.InspectionCheck;
import dev.filegate.core.spi.PrefixInspectionCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves explicit plugins once; installing a plugin never changes a policy implicitly. */
public final class CheckRegistry {
    public record RegisteredCheck(CheckDescriptor descriptor, InspectionCheck implementation) {}

    public record Plan(
            CompiledPolicy policy,
            List<RegisteredCheck> checks,
            List<PrefixInspectionCheck> prefixes) {
        public Plan {
            checks = List.copyOf(checks);
            prefixes = List.copyOf(prefixes);
        }
    }

    private final Map<String, RegisteredCheck> installed;

    public CheckRegistry(List<InspectionCheck> checks) {
        var registered = new HashMap<String, RegisteredCheck>();
        for (InspectionCheck check : checks) {
            Objects.requireNonNull(check, "check");
            CheckDescriptor descriptor = Objects.requireNonNull(check.descriptor(), "descriptor");
            if (check instanceof PrefixInspectionCheck prefix) {
                if (descriptor.kind() != CheckKind.PREFLIGHT
                        || prefix.prefixBytes() < 1
                        || prefix.prefixBytes() > PrefixInspectionCheck.MAX_PREFIX_BYTES) {
                    throw new IllegalArgumentException("Invalid bounded prefix capability");
                }
            }
            if (registered.putIfAbsent(descriptor.id(), new RegisteredCheck(descriptor, check))
                    != null) {
                throw new IllegalArgumentException("Duplicate installed check");
            }
        }
        installed = Map.copyOf(registered);
    }

    public Plan bind(CompiledPolicy policy, ResourceLimits limits) {
        Objects.requireNonNull(policy, "policy");
        if (policy.executionMode() != ExecutionMode.EMBEDDED) {
            throw new IllegalArgumentException(
                    "No isolated executor is installed; embedded fallback is forbidden");
        }
        if (policy.maxInputBytes() > limits.maxStagingBytes()) {
            throw new IllegalArgumentException("Policy input allowance exceeds the stage envelope");
        }
        var checks = new ArrayList<RegisteredCheck>();
        var prefixes = new ArrayList<PrefixInspectionCheck>();
        for (CheckDescriptor required : policy.checks()) {
            RegisteredCheck check = installed.get(required.id());
            if (check == null || !required.equals(check.descriptor())) {
                throw new IllegalArgumentException(
                        "Installed check does not match compiled capability");
            }
            checks.add(check);
            if (check.implementation() instanceof PrefixInspectionCheck prefix)
                prefixes.add(prefix);
        }
        return new Plan(policy, checks, prefixes);
    }
}
