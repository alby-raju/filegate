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
package dev.filegate.core.internal.inspection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.Inspection;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.inspection.Verdict;
import dev.filegate.core.internal.capture.CaptureResult;
import dev.filegate.core.internal.capture.StageStore;
import dev.filegate.core.resource.ResourceUsage;

/** Coordinates one immutable plan from capture to a scoped result. */
public final class InspectionEngine implements AutoCloseable {
	private final StageStore stages;
	private final CheckRunner runner = new CheckRunner();
	private final int bufferBytes;

	public InspectionEngine(StageStore stages, int bufferBytes) {
		this.stages = stages;
		this.bufferBytes = bufferBytes;
	}

	public Inspection inspect(CheckRegistry.Plan plan, InputStream source, InputClaims claims) {
		CaptureResult capture = stages.capture(source, plan.policy().maxInputBytes(), claims, plan.prefixes());
		if (capture instanceof CaptureResult.Denied denied) {
			return new BlockedInspection(
					EvidenceAggregator.aggregate(plan.policy(), denied.evidence(), List.of(denied.reason())));
		}
		var captured = (CaptureResult.Captured) capture;
		var artifact = captured.artifact();
		boolean contentIssued = false;
		try {
			var evidence = runner.run(plan, artifact, claims, captured.evidence());
			var report = EvidenceAggregator.aggregate(plan.policy(), evidence, List.of())
					.withArtifact(artifact.identity());
			if (report.verdict() == Verdict.APPROVED) {
				var result = new ApprovedInspection(report, artifact, bufferBytes);
				contentIssued = true;
				return result;
			}
			return new BlockedInspection(report);
		} finally {
			if (!contentIssued)
				artifact.close();
		}
	}

	public Inspection inspect(CheckRegistry.Plan plan, Path source, InputClaims claims) {
		Inspection result = null;
		try (var input = Files.newInputStream(source)) {
			result = inspect(plan, input, claims);
		} catch (IOException failure) {
			if (result != null)
				result.close();
			return new BlockedInspection(
					EvidenceAggregator.aggregate(plan.policy(), List.of(), List.of(ReasonCode.CAPTURE_FAILED)));
		}
		return result;
	}

	public ResourceUsage resourceUsage() {
		return stages.usage();
	}

	public void retryCleanup() {
		stages.retryCleanup();
	}

	@Override
	public void close() {
		stages.close();
	}
}
