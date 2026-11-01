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
package dev.filegate.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.FileGate;
import dev.filegate.core.inspection.Approved;
import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.spi.InspectionCheck;
import dev.filegate.core.spi.InspectionInput;

class PublicationTest {
	@TempDir
	Path temp;

	private FileGate gate() throws IOException {
		var descriptor = new CheckDescriptor("test-check", "1", "test-only-no-format-coverage", ExecutionMode.EMBEDDED);
		var policy = CompiledPolicy.compile(
				new PolicySpec("test", Use.DOWNLOAD, 32, ExecutionMode.EMBEDDED, List.of("test-check")),
				List.of(descriptor));
		var check = new InspectionCheck() {
			public CheckDescriptor descriptor() {
				return descriptor;
			}

			public CheckEvidence inspect(InspectionInput input) {
				return CheckEvidence.pass("test-check");
			}
		};
		return FileGate.open(temp, new ResourceLimits(2, 64, 1024), policy, List.of(check));
	}

	@Test
	void publishesVerifiedPersistedBytesOnlyAfterFinalizationAndCommit() throws Exception {
		try (var gate = gate();
				var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1, 2 }), InputClaims.NONE)) {
			Path published = PublicationExample.publish(((Approved) result).content(), temp);
			assertArrayEquals(new byte[] { 1, 2 }, Files.readAllBytes(published));
			assertTrue(published.getFileName().toString().startsWith("object-"));
		}
	}

	@Test
	void abandonedAndUnfinalizedDestinationsAbort() throws Exception {
		Path target;
		try (var pending = new LocalPendingObject(temp, Use.DOWNLOAD)) {
			target = pending.objectPath();
			pending.output().write(42);
			assertThrows(IllegalStateException.class, () -> pending.commit(null));
			assertFalse(Files.exists(target));
		}
		assertFalse(Files.exists(target));
		try (var files = Files.list(temp)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void wrongDestinationReceiptBlocksPublication() throws Exception {
		try (var gate = gate();
				var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1 }), InputClaims.NONE);
				var pending = new LocalPendingObject(temp, Use.DOWNLOAD)) {
			var receipt = ((Approved) result).content().copyTo(pending.output(), "different-destination", Use.DOWNLOAD);
			pending.finishWrites();
			assertThrows(IllegalArgumentException.class, () -> pending.commit(receipt));
			assertFalse(Files.exists(pending.objectPath()));
		}
	}

	@Test
	void overwrittenPendingBytesCannotBePublished() throws Exception {
		try (var gate = gate();
				var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1, 2 }), InputClaims.NONE);
				var pending = new LocalPendingObject(temp, Use.DOWNLOAD)) {
			var receipt = ((Approved) result).content().copyTo(pending.output(), pending.id(), Use.DOWNLOAD);
			// A faulty host writer adds bytes after FileGate's copy; persistence validation
			// must
			// detect it.
			pending.output().write(3);
			pending.finishWrites();
			assertThrows(IOException.class, () -> pending.commit(receipt));
			assertFalse(Files.exists(pending.objectPath()));
		}
	}

	@Test
	void failedFinalizationAndNameCollisionNeverPublishOrOverwrite() throws Exception {
		try (var gate = gate();
				var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1 }), InputClaims.NONE)) {
			var content = ((Approved) result).content();
			try (var pending = new LocalPendingObject(temp, Use.DOWNLOAD)) {
				content.copyTo(pending.output(), pending.id(), Use.DOWNLOAD);
				pending.output().close();
				assertThrows(IOException.class, pending::finishWrites);
				assertFalse(Files.exists(pending.objectPath()));
			}
			try (var pending = new LocalPendingObject(temp, Use.DOWNLOAD)) {
				var receipt = content.copyTo(pending.output(), pending.id(), Use.DOWNLOAD);
				pending.finishWrites();
				Files.write(pending.objectPath(), new byte[] { 99 });
				assertThrows(IOException.class, () -> pending.commit(receipt));
				assertArrayEquals(new byte[] { 99 }, Files.readAllBytes(pending.objectPath()));
			}
		}
	}
}
