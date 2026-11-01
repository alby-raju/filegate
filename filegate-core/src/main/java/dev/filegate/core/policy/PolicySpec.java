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

import java.util.List;
import java.util.Objects;

import dev.filegate.core.internal.validation.Identifiers;

/**
 * Typed configuration; there is deliberately no permissive string-key parser.
 */
public record PolicySpec(String name, Use use, long maxInputBytes, ExecutionMode executionMode,
		List<String> requiredChecks) {
	public static final int MAX_CHECKS = 64;

	public PolicySpec {
		name = Identifiers.requireToken(name);
		Objects.requireNonNull(use, "use");
		Objects.requireNonNull(executionMode, "executionMode");
		if (maxInputBytes <= 0) {
			throw new IllegalArgumentException("maxInputBytes must be positive");
		}
		Objects.requireNonNull(requiredChecks, "requiredChecks");
		if (requiredChecks.isEmpty() || requiredChecks.size() > MAX_CHECKS) {
			throw new IllegalArgumentException("A policy requires 1 to 64 explicit checks");
		}
		requiredChecks = List.copyOf(requiredChecks);
		requiredChecks.forEach(Identifiers::requireToken);
		if (requiredChecks.stream().distinct().count() != requiredChecks.size()) {
			throw new IllegalArgumentException("Duplicate required check");
		}
	}
}
