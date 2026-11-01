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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.content.ArtifactIdentity;

class WorkerInputTest {
	@TempDir
	Path temp;

	private ArtifactIdentity identity(byte[] bytes) throws Exception {
		return new ArtifactIdentity(bytes.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
	}

	@Test
	void boundBytesAreReadOnlyAndRemovedAfterUse() throws Exception {
		byte[] bytes = { 1, 2, 3 };
		Path path;
		try (var input = WorkerInput.capture(new ByteArrayInputStream(bytes), identity(bytes), temp, 32)) {
			path = input.path();
			assertArrayEquals(bytes, Files.readAllBytes(path));
			assertFalse(Files.getPosixFilePermissions(path).contains(PosixFilePermission.OWNER_WRITE));
			input.verifyUnchanged();
		}
		assertFalse(Files.exists(path));
	}

	@Test
	void truncatedExtendedAndWrongDigestPayloadsLeaveNoScratch() throws Exception {
		byte[] expected = { 1, 2, 3 };
		for (byte[] bytes : new byte[][] { { 1 }, { 1, 2, 3, 4 }, { 3, 2, 1 } })
			assertThrows(IOException.class, () -> WorkerInput.capture(new ByteArrayInputStream(bytes), identity(expected), temp, 32));
		try (var files = Files.list(temp)) { assertEquals(0, files.count()); }
	}

	@Test
	void mutationCannotProduceAcceptedEvidence() throws Exception {
		byte[] bytes = { 1, 2, 3 };
		try (var input = WorkerInput.capture(new ByteArrayInputStream(bytes), identity(bytes), temp, 32)) {
			Files.setPosixFilePermissions(input.path(), PosixFilePermissions.fromString("rw-------"));
			Files.write(input.path(), new byte[] { 3, 2, 1 });
			assertThrows(IOException.class, input::verifyUnchanged);
		}
	}
}
