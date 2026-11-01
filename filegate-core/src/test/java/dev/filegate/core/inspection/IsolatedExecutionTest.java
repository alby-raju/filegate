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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.FileGate;
import dev.filegate.core.policy.*;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.spi.*;

class IsolatedExecutionTest {
	@TempDir
	Path temp;

	@Test
	void immutablePolicyReachesAdapterAndFailureNeverGrantsContent() throws Exception {
		var seen = new AtomicReference<CompiledPolicy>();
		var descriptor = new CheckDescriptor("isolated", "v1", "test-only", ExecutionMode.ISOLATED);
		var adapter = new IsolatedInspectionCheck() {
			public CheckDescriptor descriptor() { return descriptor; }
			public CheckEvidence inspect(InspectionInput input, CompiledPolicy policy) {
				seen.set(policy);
				return new CheckEvidence("isolated", CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
			}
		};
		var policy = CompiledPolicy.compile(new PolicySpec("test", Use.DOWNLOAD, 32,
				ExecutionMode.ISOLATED, List.of("isolated")), List.of(descriptor));
		try (var gate = FileGate.open(temp, new ResourceLimits(1, 1024, 1024), policy, List.of(adapter));
				var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1 }), InputClaims.NONE)) {
			assertInstanceOf(Blocked.class, result);
			assertSame(policy, seen.get());
		}
	}
}
