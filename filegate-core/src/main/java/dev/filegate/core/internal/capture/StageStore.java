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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.internal.execution.BoundPrefixCheck;
import dev.filegate.core.internal.resource.ResourceLedger;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.resource.ResourceUsage;

public final class StageStore implements AutoCloseable {
	@FunctionalInterface
	interface Deleter {
		void delete(Path path) throws IOException;
	}

	private final Path root;
	private final CaptureReader reader;
	private final ResourceLedger ledger;
	private final Deleter deleter;
	private final Set<CapturedArtifact> artifacts = ConcurrentHashMap.newKeySet();
	private boolean closed;
	private int captures;

	public StageStore(Path parent, ResourceLimits limits) throws IOException {
		this(parent, limits, Files::deleteIfExists);
	}

	StageStore(Path parent, ResourceLimits limits, Deleter deleter) throws IOException {
		this.reader = new CaptureReader(limits.captureBufferBytes());
		this.ledger = new ResourceLedger(limits);
		this.deleter = deleter;
		// Unsupported permissions fail construction; there is no world-readable
		// fallback.
		this.root = Files.createTempDirectory(parent, "filegate-",
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
	}

	public CaptureResult capture(InputStream source, long maxBytes) {
		return capture(source, maxBytes, InputClaims.NONE, java.util.List.of());
	}

	public CaptureResult capture(InputStream source, long maxBytes, InputClaims claims,
			java.util.List<BoundPrefixCheck> prefixChecks) {
		var prefix = new CapturePrefix(prefixChecks, claims);
		ResourceLedger.Reservation reservation;
		synchronized (this) {
			if (closed)
				throw new IllegalStateException("Gate is closed");
			var admitted = ledger.reserve(maxBytes);
			if (admitted.isEmpty())
				return new CaptureResult.Denied(ReasonCode.CAPACITY_EXHAUSTED, java.util.List.of());
			reservation = admitted.get();
			captures++;
		}
		Path path = null;
		CapturedArtifact artifact = null;
		try {
			path = Files.createTempFile(root, "artifact-", ".stage",
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			ArtifactIdentity identity;
			try (var output = Files.newOutputStream(path)) {
				identity = reader.copy(source, output, maxBytes, prefix);
			}
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("r--------"));
			artifact = new CapturedArtifact(path, identity, this, reservation);
			artifacts.add(artifact);
			return new CaptureResult.Captured(artifact, prefix.evidence());
		} catch (CaptureFailure denied) {
			return new CaptureResult.Denied(denied.reason(), prefix.evidence());
		} catch (IOException | RuntimeException unavailable) {
			return new CaptureResult.Denied(ReasonCode.CAPTURE_FAILED, prefix.evidence());
		} finally {
			if (artifact == null)
				discard(path, reservation);
			synchronized (this) {
				captures--;
			}
			removeDeletedAndMaybeRoot();
		}
	}

	private void discard(Path path, ResourceLedger.Reservation reservation) {
		if (path == null) {
			reservation.close();
			return;
		}
		var discarded = new CapturedArtifact(path, null, this, reservation);
		artifacts.add(discarded);
		discarded.close();
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
			if (artifact.tryDelete())
				artifacts.remove(artifact);
		}
		removeDeletedAndMaybeRoot();
	}

	public ResourceUsage usage() {
		return ledger.usage((int) artifacts.stream().filter(CapturedArtifact::pendingDeletion).count());
	}

	void reclaimed(CapturedArtifact artifact) {
		artifacts.remove(artifact);
		synchronized (this) {
			if (closed && captures == 0 && artifacts.isEmpty())
				delete(root);
		}
	}

	private void removeDeletedAndMaybeRoot() {
		artifacts.removeIf(CapturedArtifact::tryDelete);
		synchronized (this) {
			if (closed && captures == 0 && artifacts.isEmpty())
				delete(root);
		}
	}

	@Override
	public void close() {
		synchronized (this) {
			closed = true;
		}
		retryCleanup();
	}
}
