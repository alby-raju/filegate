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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.spi.InspectionInput;
import dev.filegate.core.spi.IsolatedInspectionCheck;
import dev.filegate.formats.spreadsheets.RestrictedSpreadsheetInspector;
import dev.filegate.formats.spreadsheets.SpreadsheetProfile;
import dev.filegate.spreadsheets.worker.internal.SpreadsheetWorkerArguments;
import dev.filegate.spreadsheets.worker.internal.SpreadsheetWorkerMain;
import dev.filegate.worker.linux.IsolatedWorkerCheck;
import dev.filegate.worker.linux.LinuxContainerRuntime;

/** Caller-owned Linux runtime and immutable, explicitly configured basic-XLSX capability. */
public final class IsolatedSpreadsheetCheck implements IsolatedInspectionCheck {
	private final IsolatedWorkerCheck delegate;

	public IsolatedSpreadsheetCheck(String id, LinuxContainerRuntime runtime, SpreadsheetProfile profile) {
		Objects.requireNonNull(runtime, "runtime");
		Objects.requireNonNull(profile, "profile");
		try {
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(profile.toString().getBytes(StandardCharsets.UTF_8)));
			var descriptor = new CheckDescriptor(id, runtime.imageIdentity(), RestrictedSpreadsheetInspector.PROFILE + "/" + hash,
					ExecutionMode.ISOLATED);
			var arguments = new ArrayList<>(List.of(SpreadsheetWorkerMain.class.getName()));
			arguments.addAll(SpreadsheetWorkerArguments.encode(runtime.limits(), profile));
			delegate = new IsolatedWorkerCheck(descriptor, runtime, profile.packageLimits().maxInputBytes(),
					SpreadsheetWorkerMain.BUILD, arguments);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	@Override
	public CheckDescriptor descriptor() {
		return delegate.descriptor();
	}

	@Override
	public CheckEvidence inspect(InspectionInput input, CompiledPolicy policy) throws Exception {
		return delegate.inspect(input, policy);
	}
}
