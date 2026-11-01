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
package dev.filegate.worker.input;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import dev.filegate.core.content.ArtifactIdentity;

/** Owns a private, bounded worker-side copy. This is not a host approval or stage handle. */
public final class WorkerInput implements AutoCloseable {
	private final Path path;
	private final ArtifactIdentity identity;

	private WorkerInput(Path path, ArtifactIdentity identity) {
		this.path = path;
		this.identity = identity;
	}

	public static WorkerInput capture(InputStream source, ArtifactIdentity identity, Path scratch, long maximum)
			throws IOException {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(identity, "identity");
		Objects.requireNonNull(scratch, "scratch");
		if (maximum <= 0 || identity.length() > maximum)
			throw new IOException("Worker input allowance exhausted");
		Path path = Files.createTempFile(scratch, "filegate-input-", ".bin",
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		boolean retained = false;
		try {
			var digest = digest();
			long count = 0;
			try (var output = Files.newOutputStream(path)) {
				byte[] buffer = new byte[8192];
				for (int read; (read = source.read(buffer)) != -1;) {
					if (read > identity.length() - count)
						throw new IOException("Worker input exceeds its bound identity");
					count += read;
					digest.update(buffer, 0, read);
					output.write(buffer, 0, read);
				}
			}
			match(identity, count, digest);
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("r--------"));
			var input = new WorkerInput(path, identity);
			retained = true;
			return input;
		} finally {
			if (!retained) Files.deleteIfExists(path);
		}
	}

	public Path path() {
		return path;
	}

	/** Confirm that inspection did not change the bytes before accepting its evidence. */
	public void verifyUnchanged() throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != identity.length())
			throw new IOException("Worker input identity changed");
		var digest = digest();
		long count = 0;
		try (var input = Files.newInputStream(path)) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) != -1;) {
				if (read > identity.length() - count)
					throw new IOException("Worker input grew during inspection");
				count += read;
				digest.update(buffer, 0, read);
			}
		}
		match(identity, count, digest);
	}

	@Override
	public void close() throws IOException {
		Files.deleteIfExists(path);
	}

	private static void match(ArtifactIdentity identity, long count, MessageDigest digest) throws IOException {
		if (count != identity.length() || !HexFormat.of().formatHex(digest.digest()).equals(identity.sha256()))
			throw new IOException("Worker input identity does not match its request");
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
