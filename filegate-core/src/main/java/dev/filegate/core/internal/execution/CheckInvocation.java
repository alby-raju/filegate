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

package dev.filegate.core.internal.execution;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;

import java.util.concurrent.Callable;

/** One failure translation boundary for capture-time and full-content checks. */
public final class CheckInvocation {
    private CheckInvocation() {}

    public static CheckEvidence invoke(String expectedId, Callable<CheckEvidence> operation) {
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            CheckEvidence evidence = operation.call();
            if (evidence == null || !expectedId.equals(evidence.checkId())) {
                return new CheckEvidence(
                        expectedId, CheckStatus.ERROR, ReasonCode.INVALID_EVIDENCE);
            }
            return evidence;
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            return new CheckEvidence(expectedId, CheckStatus.ERROR, ReasonCode.CANCELLED);
        } catch (Exception failure) {
            return new CheckEvidence(expectedId, CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
        }
    }
}
