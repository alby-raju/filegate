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
package dev.filegate.core.internal.content;

import java.util.UUID;

import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.core.content.TransferReceipt;
import dev.filegate.core.policy.Use;

/**
 * Scoped transfer evidence, not a portable authorization or persistence token.
 */
public final class VerifiedTransferReceipt implements TransferReceipt {
	private final String transferId = UUID.randomUUID().toString();
	private final ArtifactIdentity artifact;
	private final String policyFingerprint;
	private final Use use;
	private final String pendingDestinationId;

	public VerifiedTransferReceipt(ArtifactIdentity artifact, String policyFingerprint, Use use,
			String pendingDestinationId) {
		this.artifact = artifact;
		this.policyFingerprint = policyFingerprint;
		this.use = use;
		this.pendingDestinationId = pendingDestinationId;
	}

	public String transferId() {
		return transferId;
	}

	public ArtifactIdentity artifact() {
		return artifact;
	}

	public String policyFingerprint() {
		return policyFingerprint;
	}

	public Use use() {
		return use;
	}

	public String pendingDestinationId() {
		return pendingDestinationId;
	}
}
