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
package dev.filegate.core.internal.capture;

import java.util.List;
import java.util.Objects;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.ReasonCode;

/**
 * Capture either owns sealed bytes or reports a blocking reason; never a
 * nullable tuple.
 */
public sealed interface CaptureResult {
	List<CheckEvidence> evidence();

	record Captured(CapturedArtifact artifact, List<CheckEvidence> evidence) implements CaptureResult {
		public Captured {
			Objects.requireNonNull(artifact, "artifact");
			evidence = List.copyOf(evidence);
		}
	}

	record Denied(ReasonCode reason, List<CheckEvidence> evidence) implements CaptureResult {
		public Denied {
			Objects.requireNonNull(reason, "reason");
			evidence = List.copyOf(evidence);
		}
	}
}
