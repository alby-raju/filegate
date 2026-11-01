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
package dev.filegate.spreadsheets.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import dev.filegate.formats.office.OfficeLinkPolicy;
import dev.filegate.formats.office.OfficePackageLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetProfile;
import dev.filegate.spreadsheets.worker.internal.SpreadsheetWorkerArguments;
import dev.filegate.worker.linux.SandboxLimits;

class SpreadsheetWorkerTest {
	private SpreadsheetProfile profile() {
		return new SpreadsheetProfile(new OfficePackageLimits(1024 * 1024, 8 * 1024 * 1024, 2 * 1024 * 1024,
				128, 64, 100000, 3 * 1024 * 1024), new SpreadsheetLimits(4, 100, 1000, 500, 32, 512, 256, 4096),
				OfficeLinkPolicy.HTTPS_ONLY);
	}

	@Test
	void workerConfigurationBindsEveryBudgetAndLinkPolicy() {
		var sandbox = new SandboxLimits(512L * 1024 * 1024, 64L * 1024 * 1024, 128, 1000);
		var encoded = SpreadsheetWorkerArguments.encode(sandbox, profile());
		var parsed = SpreadsheetWorkerArguments.decode(encoded.toArray(String[]::new));
		assertEquals(sandbox, parsed.sandbox());
		assertEquals(profile(), parsed.profile());
	}

	@Test
	void invalidWorkerConfigurationAndMissingRuntimeCannotActivateFallback() {
		assertThrows(NullPointerException.class, () -> new IsolatedSpreadsheetCheck("xlsx.basic", null, profile()));
		assertThrows(IllegalArgumentException.class, () -> SpreadsheetWorkerArguments.decode(new String[0]));
		var sandbox = new SandboxLimits(512L * 1024 * 1024, 64L * 1024 * 1024, 128, 1000);
		var encoded = new ArrayList<>(SpreadsheetWorkerArguments.encode(sandbox, profile()));
		encoded.set(4, Long.toString(sandbox.scratchBytes()));
		assertThrows(IllegalArgumentException.class, () -> SpreadsheetWorkerArguments.decode(encoded.toArray(String[]::new)));
	}
}
