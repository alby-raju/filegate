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
package dev.filegate.spreadsheets.worker.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.formats.spreadsheets.RestrictedSpreadsheetInspector;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;
import dev.filegate.worker.input.WorkerInput;
import dev.filegate.worker.linux.LinuxContainmentProbe;
import dev.filegate.worker.protocol.WorkerProtocol;
import dev.filegate.worker.protocol.WorkerResponse;

/** One bounded request per disposable container; only protocol frames go to stdout. */
public final class SpreadsheetWorkerMain {
	public static final String BUILD = "basic-xlsx-worker-v1-poi-6.0.0";

	private SpreadsheetWorkerMain() { }

	public static void main(String[] args) throws Exception {
		var configuration = SpreadsheetWorkerArguments.decode(args);
		LinuxContainmentProbe.verify(configuration.sandbox());
		var request = WorkerProtocol.readRequest(System.in);
		if (!request.inspectorBuild().equals(BUILD) || request.expectedChecks().size() != 1)
			throw new IOException("Spreadsheet worker contract mismatch");
		String id = request.expectedChecks().getFirst();
		CheckEvidence evidence;
		try (var captured = WorkerInput.capture(System.in, request.artifact(), Path.of("/tmp"),
				configuration.profile().packageLimits().maxInputBytes())) {
			try {
				new RestrictedSpreadsheetInspector(configuration.profile()).inspect(captured.path());
				evidence = CheckEvidence.pass(id);
			} catch (SpreadsheetViolation definite) {
				evidence = new CheckEvidence(id, CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION);
			} catch (IOException | RuntimeException uncertain) {
				evidence = new CheckEvidence(id, CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
			}
			captured.verifyUnchanged();
		}
		WorkerProtocol.writeResponse(System.out, new WorkerResponse(request, List.of(evidence)));
	}
}
