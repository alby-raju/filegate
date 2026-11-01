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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Only this component translates the supported sandbox profile to Docker arguments. */
final class DockerCommands {
    private final ContainerSpec spec;
    private final Path configuration;

    DockerCommands(ContainerSpec spec, Path configuration) {
        this.spec = spec;
        this.configuration = configuration;
    }

    List<String> create(String name, List<String> javaArguments) {
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid container name");
        if (javaArguments.isEmpty() || javaArguments.size() > 16
                || javaArguments.stream().anyMatch(arg -> arg == null || arg.length() > 256)) {
            throw new IllegalArgumentException("Unbounded worker configuration");
        }
        var command = new ArrayList<>(base("create"));
        var limits = spec.limits();
        command.addAll(List.of("--name", name, "--pull=never", "--network=none", "--read-only",
                "--user", spec.uid() + ":" + spec.gid(), "--cap-drop=ALL",
                "--security-opt=no-new-privileges=true", "--ipc=none", "--cgroupns=private",
                "--memory", Long.toString(limits.memoryBytes()),
                "--memory-swap", Long.toString(limits.memoryBytes()),
                "--cpu-period=100000", "--cpu-quota=" + limits.cpuMillisPerSecond() * 100L,
                "--pids-limit", Integer.toString(limits.pids()), "--ulimit=core=0:0", "--ulimit=nofile=64:64",
                "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=" + limits.scratchBytes() + ",mode=1777",
                "--workdir=/tmp", "--env=HOME=/tmp", "--interactive", "--entrypoint=java", spec.image(),
                "-Xmx" + limits.memoryBytes() / 2, "-XX:-UsePerfData", "-cp", "/app/*"));
        command.addAll(javaArguments);
        return List.copyOf(command);
    }

    List<String> start(String name, boolean interactive) {
        return interactive ? base("start", "--attach", "--interactive", name) : base("start", "--attach", name);
    }

    List<String> remove(String name) { return base("rm", "--force", name); }

    private List<String> base(String... arguments) {
        var command = new ArrayList<>(List.of(spec.dockerBinary().toString(), "--config", configuration.toString(),
                "--host", "unix:///var/run/docker.sock"));
        command.addAll(List.of(arguments));
        return List.copyOf(command);
    }
}
