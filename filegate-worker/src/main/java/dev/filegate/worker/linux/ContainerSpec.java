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

/** Local immutable image only. No host mounts, daemon sockets or credentials enter a task. */
public record ContainerSpec(
        Path dockerBinary, String image, int uid, int gid, SandboxLimits limits) {
    public ContainerSpec {
        if (!dockerBinary.isAbsolute()
                || image == null
                || !(image.matches("sha256:[0-9a-f]{64}")
                        || image.matches("[A-Za-z0-9./:_-]+@sha256:[0-9a-f]{64}"))
                || uid <= 0
                || gid <= 0)
            throw new IllegalArgumentException("Immutable image and non-root identity required");
        java.util.Objects.requireNonNull(limits, "limits");
    }
}
