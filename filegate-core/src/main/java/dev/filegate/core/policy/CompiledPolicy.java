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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, resolved contract. Existing operations retain this snapshot on
 * reload.
 */
public final class CompiledPolicy {
	private final PolicySpec spec;
	private final List<CheckDescriptor> checks;
	private final String fingerprint;

	private CompiledPolicy(PolicySpec spec, List<CheckDescriptor> checks) {
		this.spec = spec;
		this.checks = List.copyOf(checks);
		this.fingerprint = PolicyFingerprint.compute(spec, checks);
	}

	public static CompiledPolicy compile(PolicySpec spec, Collection<CheckDescriptor> installed) {
		Objects.requireNonNull(spec, "spec");
		Objects.requireNonNull(installed, "installed");
		Map<String, CheckDescriptor> capabilities = new HashMap<>();
		for (CheckDescriptor descriptor : installed) {
			Objects.requireNonNull(descriptor, "descriptor");
			if (capabilities.putIfAbsent(descriptor.id(), descriptor) != null) {
				throw new IllegalArgumentException("Duplicate installed check");
			}
		}
		List<CheckDescriptor> resolved = new ArrayList<>();
		for (String id : spec.requiredChecks().stream().sorted().toList()) {
			CheckDescriptor descriptor = capabilities.get(id);
			if (descriptor == null) {
				throw new IllegalArgumentException("Mandatory check is not installed: " + id);
			}
			if (descriptor.executionMode() == ExecutionMode.ISOLATED
					&& spec.executionMode() != ExecutionMode.ISOLATED) {
				throw new IllegalArgumentException("Mandatory check requires isolation");
			}
			resolved.add(descriptor);
		}
		if (resolved.stream().noneMatch(check -> check.kind() == CheckKind.CONTENT)) {
			throw new IllegalArgumentException("Preflight cannot replace mandatory content inspection");
		}
		resolved.sort(java.util.Comparator.comparing(CheckDescriptor::kind).thenComparing(CheckDescriptor::id));
		return new CompiledPolicy(spec, resolved);
	}

	public String name() {
		return spec.name();
	}

	public Use use() {
		return spec.use();
	}

	public long maxInputBytes() {
		return spec.maxInputBytes();
	}

	public ExecutionMode executionMode() {
		return spec.executionMode();
	}

	public List<CheckDescriptor> checks() {
		return checks;
	}

	public String fingerprint() {
		return fingerprint;
	}
}
