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

package dev.filegate.worker.protocol;

import dev.filegate.core.inspection.CheckEvidence;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record WorkerResponse(ProtocolBinding binding, List<CheckEvidence> evidence) {
    public WorkerResponse {
        Objects.requireNonNull(binding, "binding");
        if (evidence.size() > WorkerProtocol.MAX_CHECKS)
            throw new IllegalArgumentException("Too many findings");
        evidence = List.copyOf(evidence);
    }

    public void validateFor(ProtocolBinding expected) throws IOException {
        if (!binding.equals(expected) || evidence.size() != expected.expectedChecks().size()) {
            throw new IOException("Worker response binding mismatch");
        }
        var found = new HashSet<String>();
        for (CheckEvidence result : evidence) {
            if (!expected.expectedChecks().contains(result.checkId())
                    || !found.add(result.checkId())) {
                throw new IOException("Unexpected or duplicate worker evidence");
            }
        }
    }
}
