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

import java.nio.file.Path;

import dev.filegate.formats.spreadsheets.RestrictedSpreadsheetInspector;
import dev.filegate.spreadsheets.worker.internal.SpreadsheetWorkerArguments;
import dev.filegate.worker.input.WorkerInput;
import dev.filegate.worker.linux.LinuxContainmentProbe;
import dev.filegate.worker.protocol.WorkerProtocol;

/** Test-only entry point for replaying the synthetic CI fixture under the exact sandbox. */
public final class SyntheticSpreadsheetDiagnostic {
	private SyntheticSpreadsheetDiagnostic() { }

	public static void main(String[] args) throws Exception {
		var configuration = SpreadsheetWorkerArguments.decode(args);
		LinuxContainmentProbe.verify(configuration.sandbox());
		var request = WorkerProtocol.readRequest(System.in);
		try (var input = WorkerInput.capture(System.in, request.artifact(), Path.of("/tmp"),
				configuration.profile().packageLimits().maxInputBytes())) {
			new RestrictedSpreadsheetInspector(configuration.profile()).inspect(input.path());
			input.verifyUnchanged();
		}
	}
}
