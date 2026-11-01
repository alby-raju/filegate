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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Local immutable image only. No host mounts, daemon sockets or credentials enter a task. */
public record ContainerSpec(Path dockerBinary, String image, int uid, int gid, SandboxLimits limits) {
    public ContainerSpec {
        if (!dockerBinary.isAbsolute() || image == null
                || !(image.matches("sha256:[0-9a-f]{64}") || image.matches("[A-Za-z0-9./:_-]+@sha256:[0-9a-f]{64}"))
                || uid <= 0 || gid <= 0) throw new IllegalArgumentException("Immutable image and non-root identity required");
        java.util.Objects.requireNonNull(limits, "limits");
    }

    List<String> createCommand(Path config, String name, List<String> javaArgs) {
        ProtocolBinding.token(name);
        if (javaArgs.isEmpty() || javaArgs.size() > 16 || javaArgs.stream().anyMatch(arg -> arg.length() > 256)) {
            throw new IllegalArgumentException("Unbounded worker configuration");
        }
        var command = new ArrayList<>(List.of(dockerBinary.toString(), "--config", config.toString(),
                "--host", "unix:///var/run/docker.sock", "create", "--name", name, "--pull=never",
                "--network=none", "--read-only", "--user", uid + ":" + gid, "--cap-drop=ALL",
                "--security-opt=no-new-privileges=true", "--ipc=none", "--cgroupns=private",
                "--memory", Long.toString(limits.memoryBytes()), "--memory-swap", Long.toString(limits.memoryBytes()),
                "--cpu-period=100000", "--cpu-quota=" + limits.cpuMillisPerSecond() * 100L,
                "--pids-limit", Integer.toString(limits.pids()), "--ulimit=core=0:0", "--ulimit=nofile=64:64",
                "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=" + limits.scratchBytes() + ",mode=1777",
                "--workdir=/tmp", "--env=HOME=/tmp", "--interactive", "--entrypoint=java", image,
                "-Xmx" + limits.memoryBytes() / 2, "-XX:-UsePerfData", "-cp", "/app/*"));
        command.addAll(javaArgs);
        return List.copyOf(command);
    }
}
