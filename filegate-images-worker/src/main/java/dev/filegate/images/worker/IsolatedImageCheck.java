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
package dev.filegate.images.worker;

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
import dev.filegate.formats.images.ImageProfile;
import dev.filegate.images.worker.internal.ImageWorkerMain;
import dev.filegate.worker.linux.LinuxContainerRuntime;
import dev.filegate.worker.linux.IsolatedWorkerCheck;

/** Immutable host adapter; the caller owns and closes the Linux runtime. */
public final class IsolatedImageCheck implements IsolatedInspectionCheck {
	private final IsolatedWorkerCheck delegate;

	public IsolatedImageCheck(String id, LinuxContainerRuntime runtime, ImageProfile profile) {
		Objects.requireNonNull(runtime, "runtime");
		Objects.requireNonNull(profile, "profile");
		try {
			String coverage = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(profile.toString().getBytes(StandardCharsets.UTF_8)));
			var descriptor = new CheckDescriptor(id, runtime.imageIdentity(), "restricted-images-v1/" + coverage, ExecutionMode.ISOLATED);
			var arguments = new ArrayList<>(List.of(ImageWorkerMain.class.getName()));
			arguments.addAll(List.of(runtime.limits().arguments()));
			arguments.addAll(List.of(Long.toString(profile.maxInputBytes()), Long.toString(profile.maxPixels()),
					Long.toString(profile.maxDecodedBytes()), Long.toString(profile.maxMetadataBytes()), Integer.toString(profile.maxSegments())));
			delegate = new IsolatedWorkerCheck(descriptor, runtime, profile.maxInputBytes(), ImageWorkerMain.BUILD, arguments);
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
