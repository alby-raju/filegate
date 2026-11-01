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
package dev.filegate.images.worker.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import dev.filegate.formats.images.ImageProfile;
import dev.filegate.formats.images.RestrictedImageInspector;
import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.worker.input.WorkerInput;
import dev.filegate.worker.linux.LinuxContainmentProbe;
import dev.filegate.worker.linux.SandboxLimits;
import dev.filegate.worker.protocol.WorkerProtocol;
import dev.filegate.worker.protocol.WorkerResponse;

/** One request per container. No parsing until containment and byte binding pass. */
public final class ImageWorkerMain {
	public static final String BUILD = "restricted-images-worker-v1";

	private ImageWorkerMain() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 9)
			throw new IllegalArgumentException("Expected sandbox and image envelopes");
		var limits = SandboxLimits.parse(args, 0);
		LinuxContainmentProbe.verify(limits);
		var profile = new ImageProfile(Long.parseLong(args[4]), Long.parseLong(args[5]),
				Long.parseLong(args[6]), Long.parseLong(args[7]), Integer.parseInt(args[8]));
		var request = WorkerProtocol.readRequest(System.in);
		if (!request.inspectorBuild().equals(BUILD) || request.expectedChecks().size() != 1
				|| request.artifact().length() > profile.maxInputBytes()
				|| profile.maxInputBytes() > limits.scratchBytes() / 2)
			throw new IOException("Worker contract mismatch");
		CheckEvidence evidence;
		try (var captured = WorkerInput.capture(System.in, request.artifact(), Path.of("/tmp"), profile.maxInputBytes())) {
			evidence = new RestrictedImageInspector(profile).inspect(request.expectedChecks().getFirst(), captured.path());
			captured.verifyUnchanged();
		}
		WorkerProtocol.writeResponse(System.out, new WorkerResponse(request, List.of(evidence)));
	}
}
