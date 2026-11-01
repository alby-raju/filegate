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

package dev.filegate.core.policy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

import dev.filegate.core.internal.crypto.Sha256;

/** Versioned canonical policy encoding; independent of assembly and execution. */
final class PolicyFingerprint {
    private PolicyFingerprint() {}

    static String compute(PolicySpec spec, List<CheckDescriptor> checks) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                // Versioned, length-delimited canonical encoding, independent of locale and map order.
                output.writeUTF("filegate-policy-v2");
                output.writeUTF(spec.name());
                output.writeUTF(spec.use().name());
                output.writeLong(spec.maxInputBytes());
                output.writeUTF(spec.executionMode().name());
                output.writeInt(checks.size());
                for (CheckDescriptor check : checks) {
                    output.writeUTF(check.id());
                    output.writeUTF(check.version());
                    output.writeUTF(check.coverageProfile());
                    output.writeUTF(check.executionMode().name());
                    output.writeUTF(check.kind().name());
                }
            }
            return HexFormat.of().formatHex(Sha256.newDigest().digest(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
