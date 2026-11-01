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

package dev.filegate.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerSpecTest {
    @TempDir Path temp;
    static SandboxLimits limits() { return new SandboxLimits(256L * 1024 * 1024, 32L * 1024 * 1024, 128, 1000); }

    @Test void fixedCommandRestrictsAllDeclaredDimensionsAndNeverMountsHostFiles() {
        var spec = new ContainerSpec(Path.of("/usr/bin/docker"), "sha256:" + "a".repeat(64), 65534, 65534, limits());
        var command = spec.createCommand(temp, "filegate-test", List.of(LinuxContainmentProbe.class.getName()));
        for (String flag : List.of("--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges=true",
                "--ipc=none", "--cgroupns=private", "--memory-swap", "--pids-limit", "--tmpfs", "--pull=never")) {
            assertTrue(command.contains(flag), flag);
        }
        assertTrue(command.contains("65534:65534"));
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("--mount") || arg.equals("--privileged") || arg.equals("--env-file")));
    }

    @Test void mutableImagesRootIdentityAndInvalidBudgetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContainerSpec(Path.of("/usr/bin/docker"), "worker:latest", 1, 1, limits()));
        assertThrows(IllegalArgumentException.class, () -> new ContainerSpec(Path.of("/usr/bin/docker"), "sha256:" + "a".repeat(64), 0, 1, limits()));
        assertThrows(IllegalArgumentException.class, () -> new SandboxLimits(1, 1, 1, 1));
    }

    @Test void unsupportedRuntimeFailsBeforeAnyEmbeddedFallback() {
        var spec = new ContainerSpec(temp.resolve("missing-docker"), "sha256:" + "a".repeat(64), 65534, 65534, limits());
        assertThrows(IOException.class, () -> LinuxContainerRuntime.open(temp, spec,
                new WorkerLimits(1, Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ZERO, 1024)));
    }
}
