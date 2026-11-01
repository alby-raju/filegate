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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.spi.InspectionInput;
import dev.filegate.core.spi.IsolatedInspectionCheck;
import dev.filegate.formats.images.ImageProfile;
import dev.filegate.images.worker.internal.ImageWorkerMain;
import dev.filegate.worker.linux.LinuxContainerRuntime;
import dev.filegate.worker.process.WorkerRun;
import dev.filegate.worker.protocol.ProtocolBinding;

/** Immutable host adapter; the caller owns and closes the Linux runtime. */
public final class IsolatedImageCheck implements IsolatedInspectionCheck {
	private final LinuxContainerRuntime runtime;
	private final ImageProfile profile;
	private final CheckDescriptor descriptor;

	public IsolatedImageCheck(String id, LinuxContainerRuntime runtime, ImageProfile profile) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.profile = Objects.requireNonNull(profile, "profile");
		if (profile.maxInputBytes() > runtime.limits().scratchBytes() / 2)
			throw new IllegalArgumentException("Image input exceeds the sandbox scratch envelope");
		try {
			String coverage = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(profile.toString().getBytes(StandardCharsets.UTF_8)));
			descriptor = new CheckDescriptor(id, runtime.image(), "restricted-images-v1/" + coverage,
					ExecutionMode.ISOLATED);
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	@Override
	public CheckDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public CheckEvidence inspect(InspectionInput input, CompiledPolicy policy) throws Exception {
		if (policy.executionMode() != ExecutionMode.ISOLATED || policy.maxInputBytes() > profile.maxInputBytes()
				|| !policy.checks().contains(descriptor))
			return new CheckEvidence(descriptor.id(), CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
		var binding = new ProtocolBinding(UUID.randomUUID(), input.identity(), policy.fingerprint(),
				ImageWorkerMain.BUILD, List.of(descriptor.id()));
		var arguments = new ArrayList<>(List.of(ImageWorkerMain.class.getName()));
		arguments.addAll(List.of(runtime.limits().arguments()));
		arguments.addAll(List.of(Long.toString(profile.maxInputBytes()), Long.toString(profile.maxPixels()),
				Long.toString(profile.maxDecodedBytes()), Long.toString(profile.maxMetadataBytes()),
				Integer.toString(profile.maxSegments())));
		try (var stream = input.openStream()) {
			var run = runtime.execute(binding, stream, arguments);
			if (run instanceof WorkerRun.Completed completed)
				return completed.response().evidence().getFirst();
			return new CheckEvidence(descriptor.id(), CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
		}
	}
}
