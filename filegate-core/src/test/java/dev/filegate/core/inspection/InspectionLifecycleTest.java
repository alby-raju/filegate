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
package dev.filegate.core.inspection;

import static dev.filegate.core.testing.TestChecks.check;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.FileGate;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.spi.InspectionCheck;
import dev.filegate.core.spi.InspectionInput;
import dev.filegate.core.testing.TestChecks.Body;
import dev.filegate.core.testing.TestPolicies;

class InspectionLifecycleTest {
	@TempDir
	Path temp;

	FileGate gate(InspectionCheck... checks) throws IOException {
		return FileGate.open(temp, new ResourceLimits(2, 128, 1024),
				TestPolicies.policy(32, List.of(checks).stream().map(c -> c.descriptor().id()).toArray(String[]::new)),
				List.of(checks));
	}

	@Test
	void onlyAllRequiredEvidenceGrantsContentAndClosingReclaimsStage() throws Exception {
		try (var gate = gate(check("a", input -> {
			assertEquals("../../untrusted", input.claims().filename());
			try (var stream = input.openStream()) {
				assertArrayEquals(new byte[] { 1, 2 }, stream.readAllBytes());
			}
			return CheckEvidence.pass("a");
		}))) {
			var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1, 2 }),
					new InputClaims("../../untrusted", ""));
			var approved = assertInstanceOf(Approved.class, result);
			assertEquals(2, approved.content().identity().length());
			assertEquals(approved.content().identity(), result.report().artifact().orElseThrow());
			assertEquals(32, gate.resourceUsage().reservedStagingBytes());
			result.close();
			result.close();
			assertEquals(0, gate.resourceUsage().reservedStagingBytes());
		}
	}

	@Test
	void exceptionsAndInvalidEvidenceNeverApproveAndSkippedChecksAreNotRun() throws Exception {
		for (Body body : List.<Body>of(input -> {
			throw new IOException("secret parser detail");
		}, input -> null, input -> CheckEvidence.pass("foreign"))) {
			try (var gate = gate(check("a", body), check("b", input -> {
				fail("Must stop after missing evidence");
				return null;
			})); var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
				assertInstanceOf(Blocked.class, result);
				assertEquals(Verdict.INCOMPLETE, result.report().verdict());
				assertEquals(CheckStatus.NOT_RUN, result.report().evidence().get(1).status());
				assertEquals(0, gate.resourceUsage().reservedStagingBytes());
			}
		}
	}

	@Test
	void rejectionStopsExpensiveLaterChecks() throws Exception {
		try (var gate = gate(check("a", input -> new CheckEvidence("a", CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION)),
				check("b", input -> {
					fail("Early denial must skip later checks");
					return null;
				})); var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
			assertEquals(Verdict.REJECTED, result.report().verdict());
			assertInstanceOf(Blocked.class, result);
		}
	}

	@Test
	void inspectorAccessIsRevokedAfterInvocation() throws Exception {
		var retained = new AtomicReference<InspectionInput>();
		var stream = new AtomicReference<InputStream>();
		try (var gate = gate(check("a", input -> {
			retained.set(input);
			stream.set(input.openStream());
			assertThrows(IllegalStateException.class, input::openStream);
			return CheckEvidence.pass("a");
		})); var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
			assertEquals(Verdict.APPROVED, result.report().verdict());
			assertThrows(IllegalStateException.class, () -> retained.get().openStream());
			assertThrows(IOException.class, () -> stream.get().read());
		}
	}

	@Test
	void reloadDoesNotChangeInFlightPolicy() throws Exception {
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		try (var gate = gate(check("a", input -> {
			entered.countDown();
			proceed.await();
			return CheckEvidence.pass("a");
		})); var pool = Executors.newSingleThreadExecutor()) {
			var old = TestPolicies.policy(32, "a");
			var next = TestPolicies.policy(64, "a");
			var running = pool.submit(() -> gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE));
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			gate.reload(next);
			proceed.countDown();
			try (var result = running.get()) {
				assertEquals(old.fingerprint(), result.report().policy().fingerprint());
			}
			try (var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
				assertEquals(next.fingerprint(), result.report().policy().fingerprint());
			}
		} finally {
			proceed.countDown();
		}
	}

	@Test
	void pathInputIsCopiedAndUnavailableSourcesBlock() throws Exception {
		Path source = temp.resolve("source.bin");
		Files.write(source, new byte[] { 1 });
		try (var gate = gate(check("a", input -> {
			Files.write(source, new byte[] { 2 });
			try (var stream = input.openStream()) {
				assertEquals(1, stream.read());
			}
			return CheckEvidence.pass("a");
		})); var result = gate.inspect(source, InputClaims.NONE)) {
			assertEquals(Verdict.APPROVED, result.report().verdict());
			assertArrayEquals(new byte[] { 2 }, Files.readAllBytes(source));
			try (var missing = gate.inspect(temp.resolve("missing"), InputClaims.NONE)) {
				assertEquals(Verdict.INCOMPLETE, missing.report().verdict());
			}
		}
	}

	@Test
	void isolatedPoliciesCannotFallBackToEmbeddedAndBudgetsMustFit() {
		var check = check("a", input -> CheckEvidence.pass("a"));
		var isolated = CompiledPolicy.compile(
				new PolicySpec("p", Use.DOWNLOAD, 32, ExecutionMode.ISOLATED, List.of("a")),
				List.of(check.descriptor()));
		assertThrows(IllegalArgumentException.class,
				() -> FileGate.open(temp, new ResourceLimits(1, 32, 1024), isolated, List.of(check)));
		assertThrows(IllegalArgumentException.class, () -> FileGate.open(temp, new ResourceLimits(1, 16, 1024),
				TestPolicies.policy(32, "a"), List.of(check)));
	}
}
