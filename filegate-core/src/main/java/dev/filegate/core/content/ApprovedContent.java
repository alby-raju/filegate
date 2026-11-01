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

package dev.filegate.core.content;

import java.io.OutputStream;

import dev.filegate.core.internal.content.VerifiedContent;
import dev.filegate.core.policy.Use;

/** Scoped capability for approved bytes; no raw stage path or constructor is public. */
public sealed interface ApprovedContent permits VerifiedContent {
    ArtifactIdentity identity();
    Use use();
    String policyFingerprint();

    /** Copies to a private pending destination; the caller owns destination finalization and abort. */
    TransferReceipt copyTo(OutputStream output, String pendingDestinationId, Use destinationUse) throws TransferException;
}
