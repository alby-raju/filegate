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

package dev.filegate.core.spi;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.InputClaims;

/** A deterministic preflight check that needs no more than its declared bounded prefix. */
public interface PrefixInspectionCheck extends InspectionCheck {
    int MAX_PREFIX_BYTES = 4096;

    /** Required bytes, frozen at registration; must be between 1 and MAX_PREFIX_BYTES. */
    int prefixBytes();

    CheckEvidence inspectPrefix(byte[] prefix, InputClaims claims) throws Exception;

    @Override
    default CheckEvidence inspect(InspectionInput input) throws Exception {
        try (var stream = input.openStream()) {
            return inspectPrefix(stream.readNBytes(prefixBytes()), input.claims());
        }
    }
}
