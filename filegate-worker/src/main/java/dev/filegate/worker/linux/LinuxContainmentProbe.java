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

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/** Checks the effective cgroup-v2 task environment before any hostile-file parser runs. */
public final class LinuxContainmentProbe {
    private LinuxContainmentProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException("Expected sandbox resource limits");
        verify(SandboxLimits.parse(args, 0));
    }

    public static void verify(SandboxLimits limits) throws IOException {
        if (!System.getProperty("os.name").equals("Linux"))
            throw new IOException("Linux is required");
        Map<String, String> status =
                Files.readAllLines(Path.of("/proc/self/status")).stream()
                        .filter(line -> line.contains(":"))
                        .collect(
                                Collectors.toMap(
                                        line -> line.substring(0, line.indexOf(':')),
                                        line -> line.substring(line.indexOf(':') + 1).strip()));
        if (!"1".equals(status.get("NoNewPrivs"))
                || !"0000000000000000".equals(status.get("CapEff"))
                || status.getOrDefault("Uid", "0").matches(".*(?:^|\\s)0(?:\\s|$).*")) {
            throw new IOException("Non-root capability restrictions are missing");
        }
        requireCeiling("memory.max", limits.memoryBytes());
        requireCeiling("memory.swap.max", 0);
        requireCeiling("pids.max", limits.pids());
        String[] cpu = Files.readString(Path.of("/sys/fs/cgroup/cpu.max")).strip().split("\\s+");
        try {
            long quota = Long.parseLong(cpu[0]);
            long period = Long.parseLong(cpu[1]);
            if (quota <= 0
                    || period <= 0
                    || (double) quota / period > limits.cpuMillisPerSecond() / 1000.0 + 0.000001) {
                throw new IOException("CPU quota is not enforced");
            }
        } catch (NumberFormatException | IndexOutOfBoundsException invalid) {
            throw new IOException("Invalid CPU quota");
        }
        boolean readOnlyRoot = false;
        boolean privateScratch = false;
        for (String line : Files.readAllLines(Path.of("/proc/self/mountinfo"))) {
            String[] parts = line.split(" ");
            if (parts.length < 7) throw new IOException("Invalid mount metadata");
            var options = java.util.Set.of(parts[5].split(","));
            if (parts[4].equals("/") && options.contains("ro")) readOnlyRoot = true;
            if (parts[4].equals("/tmp")
                    && options.containsAll(java.util.Set.of("rw", "nosuid", "nodev", "noexec"))) {
                privateScratch = true;
            }
        }
        if (!readOnlyRoot
                || !privateScratch
                || Files.getFileStore(Path.of("/tmp")).getTotalSpace() > limits.scratchBytes()) {
            throw new IOException(
                    "Read-only code or bounded private scratch restrictions are missing");
        }
        for (NetworkInterface device : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (device.isUp() && !device.isLoopback())
                throw new IOException("Outbound network interface exists");
        }
    }

    private static void requireCeiling(String name, long ceiling) throws IOException {
        try {
            long actual = Long.parseLong(Files.readString(Path.of("/sys/fs/cgroup", name)).strip());
            if (actual < 0 || actual > ceiling)
                throw new IOException("Cgroup limit is not enforced");
        } catch (NumberFormatException invalid) {
            throw new IOException("Cgroup v2 numeric limit is required");
        }
    }
}
