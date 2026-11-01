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

/** Per-worker OS envelope; multiply by concurrent workers when sizing the host. */
public record SandboxLimits(long memoryBytes, long scratchBytes, int pids, int cpuMillisPerSecond) {
    public SandboxLimits {
        if (memoryBytes < 64L * 1024 * 1024
                || scratchBytes < 1024 * 1024
                || scratchBytes > memoryBytes / 4
                || pids < 32
                || pids > 1024
                || cpuMillisPerSecond < 100
                || cpuMillisPerSecond > 4000) {
            throw new IllegalArgumentException("Invalid sandbox resource envelope");
        }
    }

    public String[] arguments() {
        return new String[] {
            Long.toString(memoryBytes),
            Long.toString(scratchBytes),
            Integer.toString(pids),
            Integer.toString(cpuMillisPerSecond)
        };
    }

    public static SandboxLimits parse(String[] args, int offset) {
        return new SandboxLimits(
                Long.parseLong(args[offset]),
                Long.parseLong(args[offset + 1]),
                Integer.parseInt(args[offset + 2]),
                Integer.parseInt(args[offset + 3]));
    }
}
