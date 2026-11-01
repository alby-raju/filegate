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

import static dev.filegate.core.testing.TestChecks.descriptor;
import static dev.filegate.core.testing.TestPolicies.policy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.inspection.Verdict;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;

class PolicyEvidenceTest {
	@Test
	void completeEvidenceApprovesAndMissingEvidenceNeverDoes() {
		var policy = policy(32, "a", "b");
		var report = EvidenceAggregator.aggregate(policy, List.of(CheckEvidence.pass("b"), CheckEvidence.pass("a")),
				List.of());
		assertEquals(Verdict.APPROVED, report.verdict());
		assertEquals(List.of("a", "b"), report.evidence().stream().map(CheckEvidence::checkId).toList());
		var missing = EvidenceAggregator.aggregate(policy, List.of(CheckEvidence.pass("a")), List.of());
		assertEquals(Verdict.INCOMPLETE, missing.verdict());
		assertEquals(CheckStatus.NOT_RUN, missing.evidence().get(1).status());
		assertTrue(missing.reasons().contains(ReasonCode.MISSING_EVIDENCE));
	}

	@Test
	void duplicateForeignAndErrorEvidenceBlock() {
		var policy = policy(32, "a");
		for (var evidence : List.of(List.of(CheckEvidence.pass("a"), CheckEvidence.pass("a")),
				List.of(CheckEvidence.pass("a"), CheckEvidence.pass("foreign")),
				List.of(new CheckEvidence("a", CheckStatus.ERROR, ReasonCode.CHECK_ERROR)))) {
			assertEquals(Verdict.INCOMPLETE, EvidenceAggregator.aggregate(policy, evidence, List.of()).verdict());
		}
	}

	@Test
	void knownViolationTakesPrecedenceOverMissingOrFailedEvidence() {
		var report = EvidenceAggregator.aggregate(policy(32, "a", "b"),
				List.of(new CheckEvidence("a", CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION)),
				List.of(ReasonCode.CAPTURE_FAILED));
		assertEquals(Verdict.REJECTED, report.verdict());
		assertTrue(report.reasons().contains(ReasonCode.MISSING_EVIDENCE));
		assertEquals(Verdict.REJECTED, EvidenceAggregator
				.aggregate(policy(32, "a"), List.of(), List.of(ReasonCode.INPUT_TOO_LARGE)).verdict());
	}

	@Test
	void fingerprintsAreCanonicalAndBindEveryContractField() {
		assertEquals(policy(32, "a", "b").fingerprint(), policy(32, "b", "a").fingerprint());
		var spec = new PolicySpec("test-policy", Use.DOWNLOAD, 32, ExecutionMode.EMBEDDED, List.of("a"));
		var original = policy(32, "a");
		assertNotEquals(original.fingerprint(), policy(33, "a").fingerprint());
		assertNotEquals(original.fingerprint(),
				CompiledPolicy
						.compile(spec, List.of(new CheckDescriptor("a", "test-2", "test-only", ExecutionMode.EMBEDDED)))
						.fingerprint());
		assertNotEquals(original.fingerprint(),
				CompiledPolicy
						.compile(spec,
								List.of(new CheckDescriptor("a", "test-1", "other-profile", ExecutionMode.EMBEDDED)))
						.fingerprint());
		assertNotEquals(original.fingerprint(),
				CompiledPolicy.compile(
						new PolicySpec("test-policy", Use.EXTERNAL_SHARE, 32, ExecutionMode.EMBEDDED, List.of("a")),
						List.of(descriptor("a"))).fingerprint());
		assertEquals(original.fingerprint(),
				CompiledPolicy.compile(spec, List.of(descriptor("a"), descriptor("unused"))).fingerprint());
	}

	@Test
	void compilationRejectsAbsentDuplicateAndIncompatibleCapabilities() {
		var spec = new PolicySpec("p", Use.DOWNLOAD, 32, ExecutionMode.EMBEDDED, List.of("a"));
		assertThrows(IllegalArgumentException.class, () -> CompiledPolicy.compile(spec, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> CompiledPolicy.compile(spec, List.of(descriptor("a"), descriptor("a"))));
		assertThrows(IllegalArgumentException.class, () -> CompiledPolicy.compile(spec,
				List.of(new CheckDescriptor("a", "1", "isolated-only", ExecutionMode.ISOLATED))));
		assertThrows(IllegalArgumentException.class,
				() -> new PolicySpec("p", Use.DOWNLOAD, 0, ExecutionMode.EMBEDDED, List.of("a")));
		assertThrows(IllegalArgumentException.class,
				() -> new PolicySpec("p", Use.DOWNLOAD, 32, ExecutionMode.EMBEDDED, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new PolicySpec("p", Use.DOWNLOAD, 32, ExecutionMode.EMBEDDED, List.of("a", "a")));
		assertThrows(IllegalArgumentException.class,
				() -> new CheckEvidence("a", CheckStatus.PASS, ReasonCode.CHECK_ERROR));
	}

	@Test
	void policiesAndReportsAreSnapshots() {
		var ids = new ArrayList<>(List.of("a"));
		var spec = new PolicySpec("p", Use.DOWNLOAD, 32, ExecutionMode.EMBEDDED, ids);
		ids.add("b");
		var policy = CompiledPolicy.compile(spec, List.of(descriptor("a")));
		assertEquals(1, policy.checks().size());
		assertThrows(UnsupportedOperationException.class, () -> policy.checks().clear());
		var evidence = new ArrayList<>(List.of(CheckEvidence.pass("a")));
		var report = EvidenceAggregator.aggregate(policy, evidence, List.of());
		evidence.clear();
		assertEquals(1, report.evidence().size());
		assertThrows(UnsupportedOperationException.class, () -> report.evidence().clear());
	}
}
