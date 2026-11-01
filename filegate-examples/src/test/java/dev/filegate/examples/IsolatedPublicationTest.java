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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.FileGate;
import dev.filegate.core.inspection.Approved;
import dev.filegate.core.inspection.Blocked;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.Verdict;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.formats.images.ImageProfile;
import dev.filegate.images.worker.IsolatedImageCheck;
import dev.filegate.worker.linux.ContainerSpec;
import dev.filegate.worker.linux.LinuxContainerRuntime;
import dev.filegate.worker.linux.SandboxLimits;
import dev.filegate.worker.process.WorkerLimits;

class IsolatedPublicationTest {
	@TempDir
	Path temp;

	@Test
	@EnabledIfEnvironmentVariable(named = "FILEGATE_DOCKER_IMAGE", matches = ".+")
	void isolatedImagesPublishExactPersistedBytesAndFailuresCreateNoPublicObject() throws Exception {
		Path storage = Files.createDirectory(temp.resolve("storage"));
		var limits = new SandboxLimits(256L * 1024 * 1024, 16L * 1024 * 1024, 64, 1000);
		try (var runtime = LinuxContainerRuntime.open(temp,
				new ContainerSpec(Path.of("/usr/bin/docker"), System.getenv("FILEGATE_DOCKER_IMAGE"), 65534, 65534, limits),
				new WorkerLimits(1, Duration.ofSeconds(15), Duration.ofSeconds(2), Duration.ZERO, 2048))) {
			var check = new IsolatedImageCheck("images", runtime, new ImageProfile(1024 * 1024, 65536, 1024 * 1024, 4096, 128));
			var policy = CompiledPolicy.compile(new PolicySpec("images", Use.DOWNLOAD, 1024 * 1024,
					ExecutionMode.ISOLATED, List.of("images")), List.of(check.descriptor()));
			try (var gate = FileGate.open(temp, new ResourceLimits(1, 2 * 1024 * 1024, 8192), policy, List.of(check))) {
				for (String format : List.of("png", "jpeg")) {
					var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
					var bytes = new ByteArrayOutputStream();
					assertTrue(ImageIO.write(image, format, bytes));
					image.flush();
					try (var result = gate.inspect(new ByteArrayInputStream(bytes.toByteArray()), InputClaims.NONE)) {
						var approved = assertInstanceOf(Approved.class, result, () -> result.report().toString());
						Path stored = PublicationExample.publish(approved.content(), storage);
						assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(stored));
					}
				}
				try (var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1, 2 }), InputClaims.NONE)) {
					assertInstanceOf(Blocked.class, result);
				}
				try (var files = Files.list(storage)) {
					assertEquals(2, files.count(), "Only the two approved images may be public");
				}
				runtime.close();
				try (var result = gate.inspect(new ByteArrayInputStream(new byte[] { 1, 2 }), InputClaims.NONE)) {
					assertEquals(Verdict.INCOMPLETE, result.report().verdict());
				}
			}
		}
		try (var files = Files.list(storage)) {
			assertEquals(2, files.count(), "Closing the gate does not remove committed objects");
		}
	}
}
