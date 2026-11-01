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

package dev.filegate.core.preflight;

import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PreflightTest {


    @Test void boundedSignaturesRecognizeCandidatesButNotStructure() {
        assertEquals(FormatCandidate.PNG, CandidateRecognition.recognize(PreflightFixtures.png()));
        assertEquals(FormatCandidate.JPEG, CandidateRecognition.recognize(new byte[] {(byte) 255, (byte) 216, (byte) 255}));
        assertEquals(FormatCandidate.PDF, CandidateRecognition.recognize("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertEquals(FormatCandidate.ZIP_CONTAINER, CandidateRecognition.recognize(new byte[] {'P', 'K', 3, 4}));
        assertEquals(FormatCandidate.ZIP_CONTAINER, CandidateRecognition.recognize(new byte[] {'P', 'K', 5, 6}));
        assertEquals(FormatCandidate.UNKNOWN, CandidateRecognition.recognize(new byte[0]));
        assertEquals(FormatCandidate.UNKNOWN, CandidateRecognition.recognize(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals(FormatCandidate.UNKNOWN, CandidateRecognition.recognize(" %PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    @Test void spoofedClaimsAndUnsupportedCandidatesReject() {
        var check = new PreflightCheck(new PreflightRules(Set.of(FormatCandidate.PNG), ClaimPolicy.REQUIRE_CONSISTENT));
        assertEquals(CheckStatus.PASS, check.evaluate(PreflightFixtures.png(), new InputClaims("file.PNG", "IMAGE/PNG")).status());
        assertEquals(ReasonCode.CLAIM_MISMATCH, check.evaluate(PreflightFixtures.png(), new InputClaims("file.pdf", "image/png")).reason());
        assertEquals(ReasonCode.CLAIM_MISMATCH, check.evaluate(PreflightFixtures.png(), new InputClaims("file.png", "text/html")).reason());
        assertEquals(ReasonCode.UNSUPPORTED_FORMAT, check.evaluate(new byte[8], InputClaims.NONE).reason());
        assertEquals(CheckStatus.PASS, check.evaluate(PreflightFixtures.png(), InputClaims.NONE).status());
        var strict = new PreflightCheck(new PreflightRules(Set.of(FormatCandidate.PNG), ClaimPolicy.REQUIRE_PRESENT_AND_CONSISTENT));
        assertEquals(ReasonCode.CLAIM_MISMATCH, strict.evaluate(PreflightFixtures.png(), InputClaims.NONE).reason());
    }

    @Test void zipClaimNeverIdentifiesAnOfficePackage() {
        var check = new PreflightCheck(new PreflightRules(Set.of(FormatCandidate.ZIP_CONTAINER), ClaimPolicy.REQUIRE_CONSISTENT));
        assertEquals(CheckStatus.PASS, check.evaluate(new byte[] {'P', 'K', 3, 4},
                new InputClaims("file.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).status());
        assertEquals(FormatCandidate.ZIP_CONTAINER, CandidateRecognition.recognize(new byte[] {'P', 'K', 3, 4}));
    }

    @Test void preflightAloneCannotCompileAndRuleChangesBindFingerprint() {
        var first = new PreflightCheck(new PreflightRules(Set.of(FormatCandidate.PNG), ClaimPolicy.IGNORE));
        var next = new PreflightCheck(new PreflightRules(Set.of(FormatCandidate.PNG, FormatCandidate.PDF), ClaimPolicy.IGNORE));
        assertNotEquals(first.descriptor().coverageProfile(), next.descriptor().coverageProfile());
        assertThrows(IllegalArgumentException.class, () -> CompiledPolicy.compile(new PolicySpec("p", Use.DOWNLOAD,
                32, ExecutionMode.EMBEDDED, List.of(first.descriptor().id())), List.of(first.descriptor())));
    }
}
