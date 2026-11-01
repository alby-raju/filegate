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

package dev.filegate.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PreflightCaptureTest {
    @TempDir Path temp;

    private FileGate gate(InspectionCheck content) throws Exception {
        var preflight = new PreflightCheck(new PreflightRules(Set.of(FormatCandidate.PNG, FormatCandidate.JPEG),
                ClaimPolicy.REQUIRE_CONSISTENT));
        var spec = new PolicySpec("images", Use.DOWNLOAD, 128, ExecutionMode.EMBEDDED,
                List.of(preflight.descriptor().id(), content.descriptor().id()));
        var policy = CompiledPolicy.compile(spec, List.of(preflight.descriptor(), content.descriptor()));
        return FileGate.open(temp, new ResourceLimits(1, 128, 1024), policy, List.of(preflight, content));
    }

    @Test void unsupportedPrefixRejectsAfterEightBytesWithoutCallingContentInspector() throws Exception {
        var source = new ByteArrayInputStream(new byte[100]);
        try (var gate = gate(InspectionLifecycleTest.check("a.content", input -> { fail("Must not run"); return null; }));
             var result = gate.inspect(source, InputClaims.NONE)) {
            assertEquals(Verdict.REJECTED, result.report().verdict());
            assertEquals(92, source.available());
            assertEquals("core.preflight", result.report().evidence().getFirst().checkId());
            assertEquals(CheckStatus.FAIL, result.report().evidence().getFirst().status());
            assertEquals(CheckStatus.NOT_RUN, result.report().evidence().getLast().status());
            assertEquals(0, gate.resourceUsage().reservedStagingBytes());
        }
    }

    @Test void aSignatureMatchStillRequiresWholeCaptureAndContentEvidence() throws Exception {
        byte[] bytes = new byte[64];
        System.arraycopy(PreflightTest.PNG, 0, bytes, 0, 8);
        try (var gate = gate(InspectionLifecycleTest.check("a.content", input -> {
            assertEquals(64, input.identity().length());
            return new CheckEvidence("a.content", CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
        })); var result = gate.inspect(new ByteArrayInputStream(bytes), InputClaims.NONE)) {
            assertEquals(Verdict.INCOMPLETE, result.report().verdict());
            assertInstanceOf(Blocked.class, result);
            assertEquals(CheckStatus.PASS, result.report().evidence().getFirst().status());
        }
    }

    @Test void claimConflictRejectsEarlyAndTruncatedSignatureRejectsAtEof() throws Exception {
        try (var gate = gate(InspectionLifecycleTest.check("a.content", input -> { fail("Must not run"); return null; }))) {
            try (var conflict = gate.inspect(new ByteArrayInputStream(PreflightTest.PNG), new InputClaims("file.pdf", "image/png"))) {
                assertEquals(Verdict.REJECTED, conflict.report().verdict());
                assertTrue(conflict.report().reasons().contains(ReasonCode.CLAIM_MISMATCH));
            }
            try (var truncated = gate.inspect(new ByteArrayInputStream(new byte[] {(byte) 0x89, 'P', 'N', 'G'}), InputClaims.NONE)) {
                assertEquals(Verdict.REJECTED, truncated.report().verdict());
                assertTrue(truncated.report().reasons().contains(ReasonCode.UNSUPPORTED_FORMAT));
            }
        }
    }

    @Test void shortRecognizableCandidateAndBrokenZeroReadStreamMakeProgress() throws Exception {
        var source = new ByteArrayInputStream(new byte[] {(byte) 255, (byte) 216, (byte) 255}) {
            private boolean first = true;
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                if (first) { first = false; return 0; }
                return super.read(bytes, offset, length);
            }
        };
        try (var gate = gate(InspectionLifecycleTest.check("a.content", input ->
                new CheckEvidence("a.content", CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION)));
             var result = gate.inspect(source, InputClaims.NONE)) {
            assertEquals(CheckStatus.PASS, result.report().evidence().getFirst().status());
            assertEquals(Verdict.REJECTED, result.report().verdict());
        }
    }
}
