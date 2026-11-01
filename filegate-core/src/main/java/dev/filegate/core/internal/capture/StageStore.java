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

import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.internal.crypto.Digests;
import dev.filegate.core.internal.resource.ResourceLedger;
import dev.filegate.core.preflight.CandidateRecognition;
import dev.filegate.core.preflight.PreflightCheck;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.resource.ResourceUsage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StageStore implements AutoCloseable {
    @FunctionalInterface interface Deleter { void delete(Path path) throws IOException; }
    public record Capture(CapturedArtifact artifact, ReasonCode failure, CheckEvidence preflightEvidence) {
        public Capture(CapturedArtifact artifact, ReasonCode failure) { this(artifact, failure, null); }
    }

    private final Path root;
    private final ResourceLimits limits;
    private final ResourceLedger ledger;
    private final Deleter deleter;
    private final Set<CapturedArtifact> artifacts = ConcurrentHashMap.newKeySet();
    private boolean closed;
    private int captures;

    public StageStore(Path parent, ResourceLimits limits) throws IOException {
        this(parent, limits, Files::deleteIfExists);
    }

    StageStore(Path parent, ResourceLimits limits, Deleter deleter) throws IOException {
        this.limits = limits;
        this.ledger = new ResourceLedger(limits);
        this.deleter = deleter;
        // Unsupported permissions fail construction; there is no world-readable fallback.
        this.root = Files.createTempDirectory(parent, "filegate-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    public Capture capture(InputStream source, long maxBytes) {
        return capture(source, maxBytes, InputClaims.NONE, null);
    }

    public Capture capture(InputStream source, long maxBytes, InputClaims claims, PreflightCheck preflight) {
        ResourceLedger.Reservation reservation;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Gate is closed");
            var admitted = ledger.reserve(maxBytes);
            if (admitted.isEmpty()) return new Capture(null, ReasonCode.CAPACITY_EXHAUSTED);
            reservation = admitted.get();
            captures++;
        }
        Path path = null;
        CapturedArtifact captured = null;
        ReasonCode failure = null;
        CheckEvidence preflightEvidence = null;
        try {
            path = Files.createTempFile(root, "artifact-", ".stage",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            var digest = Digests.sha256();
            long count = 0;
            byte[] buffer = new byte[limits.captureBufferBytes()];
            byte[] prefix = new byte[CandidateRecognition.PREFIX_BYTES];
            int prefixLength = 0;
            try (var output = Files.newOutputStream(path)) {
                while (true) {
                    if (Thread.currentThread().isInterrupted()) {
                        failure = ReasonCode.CANCELLED;
                        break;
                    }
                    // Read at most one byte beyond the policy limit, without overflow.
                    int wanted = (int) Math.min(buffer.length,
                            maxBytes - count >= buffer.length ? buffer.length : maxBytes - count + 1);
                    if (preflight != null && preflightEvidence == null) {
                        wanted = Math.min(wanted, prefix.length - prefixLength);
                    }
                    int read = source.read(buffer, 0, wanted);
                    if (read == -1) {
                        if (preflight != null && preflightEvidence == null) {
                            preflightEvidence = preflight.evaluate(java.util.Arrays.copyOf(prefix, prefixLength), claims);
                            if (preflightEvidence.status() != CheckStatus.PASS) failure = preflightEvidence.reason();
                        }
                        break;
                    }
                    if (read == 0) {
                        // Defensive progress for broken streams; host still owns transport timeouts.
                        int next = source.read();
                        if (next == -1) {
                            if (preflight != null && preflightEvidence == null) {
                                preflightEvidence = preflight.evaluate(java.util.Arrays.copyOf(prefix, prefixLength), claims);
                                if (preflightEvidence.status() != CheckStatus.PASS) failure = preflightEvidence.reason();
                            }
                            break;
                        }
                        buffer[0] = (byte) next;
                        read = 1;
                    }
                    if (read > maxBytes - count) {
                        failure = ReasonCode.INPUT_TOO_LARGE;
                        break;
                    }
                    if (preflight != null && preflightEvidence == null) {
                        System.arraycopy(buffer, 0, prefix, prefixLength, read);
                        prefixLength += read;
                        if (prefixLength == prefix.length) {
                            preflightEvidence = preflight.evaluate(prefix, claims);
                            if (preflightEvidence.status() != CheckStatus.PASS) {
                                failure = preflightEvidence.reason();
                                break;
                            }
                        }
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    count += read;
                }
            }
            if (failure == null) {
                // Close capture writer, then remove write permission before any inspector runs.
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("r--------"));
                captured = new CapturedArtifact(path,
                        new ArtifactIdentity(count, HexFormat.of().formatHex(digest.digest())), this, reservation);
                artifacts.add(captured);
            }
        } catch (IOException | RuntimeException e) {
            if (failure == null) failure = ReasonCode.CAPTURE_FAILED;
        } finally {
            if (captured == null) {
                if (path == null) {
                    reservation.close();
                } else {
                    var discarded = new CapturedArtifact(path, null, this, reservation);
                    artifacts.add(discarded);
                    discarded.close();
                }
            }
            synchronized (this) { captures--; }
            removeDeletedAndMaybeRoot();
        }
        return new Capture(captured, failure, preflightEvidence);
    }

    boolean delete(Path path) {
        try {
            deleter.delete(path);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public void retryCleanup() {
        for (CapturedArtifact artifact : artifacts) {
            if (artifact.tryDelete()) artifacts.remove(artifact);
        }
        removeDeletedAndMaybeRoot();
    }

    public ResourceUsage usage() {
        return ledger.usage((int) artifacts.stream().filter(CapturedArtifact::pendingDeletion).count());
    }

    void reclaimed(CapturedArtifact artifact) {
        artifacts.remove(artifact);
        synchronized (this) {
            if (closed && captures == 0 && artifacts.isEmpty()) delete(root);
        }
    }

    private void removeDeletedAndMaybeRoot() {
        artifacts.removeIf(CapturedArtifact::tryDelete);
        synchronized (this) {
            if (closed && captures == 0 && artifacts.isEmpty()) delete(root);
        }
    }

    @Override public void close() {
        synchronized (this) { closed = true; }
        retryCleanup();
    }
}
