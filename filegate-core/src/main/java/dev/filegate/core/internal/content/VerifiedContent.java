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

import dev.filegate.core.content.ApprovedContent;
import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.core.content.TransferException;
import dev.filegate.core.content.TransferFailure;
import dev.filegate.core.content.TransferReceipt;
import dev.filegate.core.internal.capture.CapturedArtifact;
import dev.filegate.core.internal.crypto.Digests;
import dev.filegate.core.internal.validation.Names;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.Use;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;
import java.util.Objects;

/** Scoped capability for approved bytes. Publication APIs verify bytes on every copy. */
public final class VerifiedContent implements ApprovedContent, AutoCloseable {
    private final CapturedArtifact artifact;
    private final CompiledPolicy policy;
    private final int bufferBytes;

    public VerifiedContent(CapturedArtifact artifact, CompiledPolicy policy, int bufferBytes) {
        this.artifact = artifact;
        this.policy = policy;
        this.bufferBytes = bufferBytes;
    }

    public ArtifactIdentity identity() { return artifact.identity(); }
    public Use use() { return policy.use(); }
    public String policyFingerprint() { return policy.fingerprint(); }

    /**
     * Copy only to a private pending destination. Caller owns the destination stream;
     * this method neither flushes nor closes it. Abort on any failure. Generic streams
     * require host-provided I/O timeouts. A receipt proves streamed bytes, not persistence.
     */
    public TransferReceipt copyTo(OutputStream output, String pendingDestinationId, Use destinationUse)
            throws TransferException {
        Objects.requireNonNull(output, "output");
        Names.token(pendingDestinationId);
        Objects.requireNonNull(destinationUse, "destinationUse");
        if (destinationUse != policy.use()) {
            throw new IllegalArgumentException("Destination use differs from approved use");
        }
        try (var lease = artifact.retain(); var input = lease.open()) {
            var digest = Digests.sha256();
            byte[] buffer = new byte[bufferBytes];
            long count = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new TransferException(TransferFailure.CANCELLED);
                long remaining = artifact.identity().length() - count;
                int wanted = (int) (remaining >= buffer.length ? buffer.length : remaining + 1);
                int read = input.read(buffer, 0, wanted);
                if (read == -1) break;
                if (read > remaining) throw new TransferException(TransferFailure.INTEGRITY_MISMATCH);
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                count += read;
            }
            if (count != artifact.identity().length()
                    || !HexFormat.of().formatHex(digest.digest()).equals(artifact.identity().sha256())) {
                throw new TransferException(TransferFailure.INTEGRITY_MISMATCH);
            }
            return new VerifiedTransferReceipt(artifact.identity(), policy.fingerprint(), policy.use(), pendingDestinationId);
        } catch (TransferException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new TransferException(TransferFailure.IO_FAILURE);
        }
    }

    @Override public void close() { artifact.close(); }
}
