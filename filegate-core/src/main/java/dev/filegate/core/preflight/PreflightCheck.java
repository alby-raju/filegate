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
package dev.filegate.core.preflight;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.internal.crypto.Sha256;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CheckKind;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.spi.PrefixInspectionCheck;

/** Signature and claim checks only. A content inspector is still mandatory. */
public final class PreflightCheck implements PrefixInspectionCheck {
	private final PreflightRules rules;
	private final CheckDescriptor descriptor;

	public PreflightCheck(PreflightRules rules) {
		this.rules = java.util.Objects.requireNonNull(rules, "rules");
		String canonical = rules.claims().name() + ":" + rules.allowedCandidates().stream().map(Enum::name).sorted()
				.collect(java.util.stream.Collectors.joining(","));
		String hash = HexFormat.of()
				.formatHex(Sha256.newDigest().digest(canonical.getBytes(StandardCharsets.US_ASCII)));
		descriptor = new CheckDescriptor("core.preflight", "1", "signature-claims-v1-" + hash, ExecutionMode.EMBEDDED,
				CheckKind.PREFLIGHT);
	}

	@Override
	public CheckDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public int prefixBytes() {
		return CandidateRecognition.PREFIX_BYTES;
	}

	@Override
	public CheckEvidence inspectPrefix(byte[] prefix, InputClaims claims) {

		FormatCandidate candidate = CandidateRecognition.recognize(prefix);
		if (!rules.allowedCandidates().contains(candidate)) {
			return new CheckEvidence(descriptor.id(), CheckStatus.FAIL, ReasonCode.UNSUPPORTED_FORMAT);
		}
		if (rules.claims() != ClaimPolicy.IGNORE) {
			boolean present = !claims.filename().isEmpty() && !claims.mediaType().isEmpty();
			if ((rules.claims() == ClaimPolicy.REQUIRE_PRESENT_AND_CONSISTENT && !present)
					|| (!claims.filename().isEmpty() && extensionCandidate(claims.filename()) != candidate)
					|| (!claims.mediaType().isEmpty() && mediaCandidate(claims.mediaType()) != candidate)) {
				return new CheckEvidence(descriptor.id(), CheckStatus.FAIL, ReasonCode.CLAIM_MISMATCH);
			}
		}
		return CheckEvidence.pass(descriptor.id());
	}

	private static FormatCandidate extensionCandidate(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0)
			return FormatCandidate.UNKNOWN;
		return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
		case "png" -> FormatCandidate.PNG;
		case "jpg", "jpeg" -> FormatCandidate.JPEG;
		case "pdf" -> FormatCandidate.PDF;
		case "zip", "docx", "xlsx" -> FormatCandidate.ZIP_CONTAINER;
		default -> FormatCandidate.UNKNOWN;
		};
	}

	private static FormatCandidate mediaCandidate(String mediaType) {
		String base = mediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
		return switch (base) {
		case "image/png" -> FormatCandidate.PNG;
		case "image/jpeg" -> FormatCandidate.JPEG;
		case "application/pdf" -> FormatCandidate.PDF;
		case "application/zip", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
			FormatCandidate.ZIP_CONTAINER;
		default -> FormatCandidate.UNKNOWN;
		};
	}
}
