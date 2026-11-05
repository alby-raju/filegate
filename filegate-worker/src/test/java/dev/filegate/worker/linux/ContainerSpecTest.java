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
package dev.filegate.worker.linux;

import static dev.filegate.worker.testing.WorkerFixtures.sandboxLimits;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.worker.process.WorkerLimits;

class ContainerSpecTest {
	@TempDir
	Path temp;

	@Test
	void fixedCommandRestrictsAllDeclaredDimensionsAndNeverMountsHostFiles() {
		var spec = new ContainerSpec(Path.of("/usr/bin/docker"), "sha256:" + "a".repeat(64), 65534, 65534,
				sandboxLimits());
		var command = new DockerCommands(spec, temp).create("filegate-test",
				List.of(LinuxContainmentProbe.class.getName()));
		for (String flag : List.of("--network=none", "--read-only", "--cap-drop=ALL",
				"--security-opt=no-new-privileges=true", "--ipc=none", "--cgroupns=private", "--memory-swap",
				"--pids-limit", "--tmpfs", "--pull=never")) {
			assertTrue(command.contains(flag), flag);
		}
		assertTrue(command.contains("65534:65534"));
		assertFalse(command.stream()
				.anyMatch(arg -> arg.startsWith("--mount") || arg.equals("--privileged") || arg.equals("--env-file")));
	}

	@Test
	void commandAcceptsTheSpreadsheetWorkerArgumentCountAndRejectsLargerConfigurations() {
		var spec = new ContainerSpec(Path.of("/usr/bin/docker"), "sha256:" + "a".repeat(64), 65534, 65534,
				sandboxLimits());
		var commands = new DockerCommands(spec, temp);
		var arguments = new ArrayList<>(List.of("dev.filegate.spreadsheets.worker.internal.SpreadsheetWorkerMain"));
		arguments.addAll(Collections.nCopies(20, "configuration"));

		var command = commands.create("filegate-test", arguments);
		assertEquals(arguments, command.subList(command.size() - arguments.size(), command.size()));

		arguments.add("extra");
		assertThrows(IllegalArgumentException.class, () -> commands.create("filegate-test", arguments));
	}

	@Test
	void mutableImagesRootIdentityAndInvalidBudgetsAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new ContainerSpec(Path.of("/usr/bin/docker"), "worker:latest", 1, 1, sandboxLimits()));
		assertThrows(IllegalArgumentException.class,
				() -> new ContainerSpec(Path.of("/usr/bin/docker"), "sha256:" + "a".repeat(64), 0, 1, sandboxLimits()));
		assertThrows(IllegalArgumentException.class, () -> new SandboxLimits(1, 1, 1, 1));
	}

	@Test
	void unsupportedRuntimeFailsBeforeAnyEmbeddedFallback() {
		var spec = new ContainerSpec(temp.resolve("missing-docker"), "sha256:" + "a".repeat(64), 65534, 65534,
				sandboxLimits());
		assertThrows(IOException.class, () -> LinuxContainerRuntime.open(temp, spec,
				new WorkerLimits(1, Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ZERO, 1024)));
	}
	@Test
	void immutableImageAliasesHaveTheSameBoundedContentIdentity() {
		String digest = "sha256:" + "a".repeat(64);
		for (String image : List.of(digest, "registry.example.invalid/team/worker@" + digest))
			assertEquals(digest, new ContainerSpec(Path.of("/usr/bin/docker"), image, 65534, 65534, sandboxLimits()).imageIdentity());
	}

}
