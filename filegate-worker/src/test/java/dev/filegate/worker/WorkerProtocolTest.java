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

import dev.filegate.core.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerProtocolTest {
    private ProtocolBinding binding() {
        return new ProtocolBinding(UUID.randomUUID(), new ArtifactIdentity(123, "a".repeat(64)),
                "b".repeat(64), "worker-test-1", List.of("a", "b"));
    }

    private byte[] response(ProtocolBinding binding, List<CheckEvidence> evidence) throws IOException {
        var bytes = new ByteArrayOutputStream();
        WorkerProtocol.writeResponse(bytes, new WorkerResponse(binding, evidence));
        return bytes.toByteArray();
    }

    @Test void roundTripsCompleteBindingAndEvidence() throws Exception {
        var expected = binding();
        var bytes = new ByteArrayOutputStream();
        WorkerProtocol.writeRequest(bytes, expected);
        assertEquals(expected, WorkerProtocol.readRequest(new ByteArrayInputStream(bytes.toByteArray())));
        var evidence = List.of(CheckEvidence.pass("a"), new CheckEvidence("b", CheckStatus.ERROR, ReasonCode.CHECK_ERROR));
        assertEquals(evidence, WorkerProtocol.readResponse(new ByteArrayInputStream(response(expected, evidence)), expected).evidence());
    }

    @Test void everyBindingFieldIsValidated() throws Exception {
        var original = binding();
        var evidence = List.of(CheckEvidence.pass("a"), CheckEvidence.pass("b"));
        for (var changed : List.of(
                new ProtocolBinding(UUID.randomUUID(), original.artifact(), original.policyFingerprint(), original.inspectorBuild(), original.expectedChecks()),
                new ProtocolBinding(original.requestId(), new ArtifactIdentity(124, "a".repeat(64)), original.policyFingerprint(), original.inspectorBuild(), original.expectedChecks()),
                new ProtocolBinding(original.requestId(), new ArtifactIdentity(123, "c".repeat(64)), original.policyFingerprint(), original.inspectorBuild(), original.expectedChecks()),
                new ProtocolBinding(original.requestId(), original.artifact(), "c".repeat(64), original.inspectorBuild(), original.expectedChecks()),
                new ProtocolBinding(original.requestId(), original.artifact(), original.policyFingerprint(), "other-build", original.expectedChecks()),
                new ProtocolBinding(original.requestId(), original.artifact(), original.policyFingerprint(), original.inspectorBuild(), List.of("a", "c")))) {
            byte[] encoded = response(changed, evidence);
            assertThrows(IOException.class, () -> WorkerProtocol.readResponse(new ByteArrayInputStream(encoded), original));
        }
    }

    @Test void missingDuplicateAndUnknownEvidenceBlock() throws Exception {
        var binding = binding();
        for (var evidence : List.of(List.<CheckEvidence>of(), List.of(CheckEvidence.pass("a")),
                List.of(CheckEvidence.pass("a"), CheckEvidence.pass("a")),
                List.of(CheckEvidence.pass("a"), CheckEvidence.pass("foreign")))) {
            byte[] encoded = response(binding, evidence);
            assertThrows(IOException.class, () -> WorkerProtocol.readResponse(new ByteArrayInputStream(encoded), binding));
        }
    }

    @Test void oversizedNegativeTruncatedWrongVersionAndTrailingFramesBlock() throws Exception {
        var binding = binding();
        for (int length : new int[] {-1, 0, 4, Integer.MAX_VALUE, WorkerProtocol.MAX_FRAME_BYTES + 1}) {
            byte[] frame = ByteBuffer.allocate(4).putInt(length).array();
            assertThrows(IOException.class, () -> WorkerProtocol.readResponse(new ByteArrayInputStream(frame), binding));
        }
        byte[] valid = response(binding, List.of(CheckEvidence.pass("a"), CheckEvidence.pass("b")));
        for (int length : new int[] {0, 3, 4, valid.length - 1}) {
            byte[] truncated = java.util.Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> WorkerProtocol.readResponse(new ByteArrayInputStream(truncated), binding));
        }
        byte[] wrongVersion = valid.clone();
        wrongVersion[4] ^= 1;
        assertThrows(IOException.class, () -> WorkerProtocol.readResponse(new ByteArrayInputStream(wrongVersion), binding));
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        ByteBuffer.wrap(trailing).putInt(trailing.length - 4);
        assertThrows(IOException.class, () -> WorkerProtocol.readResponse(new ByteArrayInputStream(trailing), binding));
        assertThrows(IOException.class, () -> WorkerProtocol.readRequest(new ByteArrayInputStream(valid)));
    }

    @Test void maximumSupportedCheckSetFitsTheFrameBudget() throws Exception {
        var ids = java.util.stream.IntStream.range(0, PolicySpec.MAX_CHECKS)
                .mapToObj(i -> "check-" + i + "x".repeat(115)).toList();
        var binding = new ProtocolBinding(UUID.randomUUID(), new ArtifactIdentity(0, "a".repeat(64)),
                "b".repeat(64), "build", ids);
        byte[] encoded = response(binding, ids.stream().map(CheckEvidence::pass).toList());
        assertTrue(encoded.length <= WorkerProtocol.MAX_FRAME_BYTES + 4);
        assertEquals(64, WorkerProtocol.readResponse(new ByteArrayInputStream(encoded), binding).evidence().size());
    }

    @Test void randomFramesCannotEscapeTheProtocolFailureContract() throws Exception {
        var random = new java.util.Random(0x4647);
        var expected = binding();
        for (int i = 0; i < 500; i++) {
            byte[] junk = new byte[random.nextInt(512) + 5];
            random.nextBytes(junk);
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(junk.length);
            output.write(junk);
            assertThrows(IOException.class,
                    () -> WorkerProtocol.readResponse(new ByteArrayInputStream(bytes.toByteArray()), expected));
        }
    }
}
