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

package dev.filegate.core.internal.capture;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.internal.execution.BoundPrefixCheck;
import dev.filegate.core.internal.execution.CheckInvocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Per-capture state; independent of any concrete format or claim checker. */
final class CapturePrefix {
    private final List<BoundPrefixCheck> required;
    private final List<CheckEvidence> evidence = new ArrayList<>();
    private final InputClaims claims;
    private final byte[] prefix;
    private int length;
    private int completed;

    CapturePrefix(List<BoundPrefixCheck> checks, InputClaims claims) {
        this.required = List.copyOf(checks);
        int capacity = required.stream().mapToInt(BoundPrefixCheck::bytes).max().orElse(0);
        this.prefix = new byte[capacity];
        this.claims = claims;
    }

    int readLimit() {
        return completed == required.size()
                ? Integer.MAX_VALUE
                : required.get(completed).bytes() - length;
    }

    void accept(byte[] bytes, int count) throws CaptureFailure {
        if (completed == required.size()) return;
        System.arraycopy(bytes, 0, prefix, length, count);
        length += count;
        inspectReady(false);
    }

    void finish() throws CaptureFailure {
        inspectReady(true);
    }

    List<CheckEvidence> evidence() {
        return List.copyOf(evidence);
    }

    private void inspectReady(boolean atEnd) throws CaptureFailure {
        while (completed < required.size()) {
            BoundPrefixCheck next = required.get(completed);
            if (!atEnd && length < next.bytes()) return;
            byte[] bounded = Arrays.copyOf(prefix, Math.min(length, next.bytes()));
            CheckEvidence result =
                    CheckInvocation.invoke(
                            next.id(), () -> next.implementation().inspectPrefix(bounded, claims));
            evidence.add(result);
            completed++;
            if (result.status() != CheckStatus.PASS) throw new CaptureFailure(result.reason());
        }
    }
}
