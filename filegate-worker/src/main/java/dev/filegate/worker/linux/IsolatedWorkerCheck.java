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
package dev.filegate.worker.linux;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CheckKind;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.spi.InspectionInput;
import dev.filegate.core.spi.IsolatedInspectionCheck;
import dev.filegate.worker.process.WorkerRun;
import dev.filegate.worker.protocol.ProtocolBinding;

/** Common host-side adapter; the caller owns the runtime and installs a trusted immutable program. */
public final class IsolatedWorkerCheck implements IsolatedInspectionCheck {
	private final CheckDescriptor descriptor;
	private final LinuxContainerRuntime runtime;
	private final long maximum;
	private final String build;
	private final List<String> arguments;

	public IsolatedWorkerCheck(CheckDescriptor descriptor, LinuxContainerRuntime runtime, long maximum,
			String build, List<String> arguments) {
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.arguments = List.copyOf(arguments);
		if (descriptor.executionMode() != ExecutionMode.ISOLATED || descriptor.kind() != CheckKind.CONTENT
				|| !descriptor.version().equals(runtime.imageIdentity()) || maximum <= 0
				|| maximum > runtime.limits().scratchBytes() / 2 || this.arguments.isEmpty()
				|| build == null || !build.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))
			throw new IllegalArgumentException("Invalid bound worker program or envelope");
		this.maximum = maximum;
		this.build = build;
	}

	@Override
	public CheckDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public CheckEvidence inspect(InspectionInput input, CompiledPolicy policy) throws Exception {
		if (policy.executionMode() != ExecutionMode.ISOLATED || policy.maxInputBytes() > maximum
				|| input.identity().length() > maximum || !policy.checks().contains(descriptor))
			return failed();
		var binding = new ProtocolBinding(UUID.randomUUID(), input.identity(), policy.fingerprint(), build, List.of(descriptor.id()));
		try (var stream = input.openStream()) {
			var result = runtime.execute(binding, stream, arguments);
			if (result instanceof WorkerRun.Completed completed)
				return completed.response().evidence().getFirst();
			return failed();
		}
	}

	private CheckEvidence failed() {
		return new CheckEvidence(descriptor.id(), CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
	}
}
