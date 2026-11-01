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
package dev.filegate.core.testing;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.spi.InspectionCheck;
import dev.filegate.core.spi.InspectionInput;

/**
 * Explicitly test-only checks; these helpers do not provide format coverage.
 */
public final class TestChecks {
	private TestChecks() {
	}

	@FunctionalInterface
	public interface Body {
		CheckEvidence inspect(InspectionInput input) throws Exception;
	}

	public static CheckDescriptor descriptor(String id) {
		return new CheckDescriptor(id, "test-1", "test-only", ExecutionMode.EMBEDDED);
	}

	public static InspectionCheck check(String id, Body body) {
		return new InspectionCheck() {
			@Override
			public CheckDescriptor descriptor() {
				return TestChecks.descriptor(id);
			}

			@Override
			public CheckEvidence inspect(InspectionInput input) throws Exception {
				return body.inspect(input);
			}
		};
	}
}
