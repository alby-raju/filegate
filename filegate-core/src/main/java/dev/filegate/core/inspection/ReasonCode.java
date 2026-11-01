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
package dev.filegate.core.inspection;

/**
 * Stable, bounded diagnostics. No filenames, removed content or parser
 * messages.
 */
public enum ReasonCode {
	CHECK_PASSED(CheckStatus.PASS), POLICY_VIOLATION(CheckStatus.FAIL), INPUT_TOO_LARGE(CheckStatus.FAIL),
	UNSUPPORTED_FORMAT(CheckStatus.FAIL), CLAIM_MISMATCH(CheckStatus.FAIL), CHECK_ERROR(CheckStatus.ERROR),
	CANCELLED(CheckStatus.ERROR), CAPACITY_EXHAUSTED(CheckStatus.ERROR), CAPTURE_FAILED(CheckStatus.ERROR),
	CLEANUP_PENDING(CheckStatus.ERROR), INVALID_EVIDENCE(CheckStatus.ERROR), MISSING_EVIDENCE(CheckStatus.ERROR),
	CHECK_NOT_RUN(CheckStatus.NOT_RUN);

	private final CheckStatus status;

	ReasonCode(CheckStatus status) {
		this.status = status;
	}

	public CheckStatus status() {
		return status;
	}
}
