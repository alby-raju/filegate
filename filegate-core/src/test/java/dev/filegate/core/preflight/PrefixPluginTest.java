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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.FileGate;
import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.inspection.Verdict;
import dev.filegate.core.policy.CheckDescriptor;
import dev.filegate.core.policy.CheckKind;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.spi.InspectionCheck;
import dev.filegate.core.spi.PrefixInspectionCheck;
import dev.filegate.core.testing.TestChecks;

import static org.junit.jupiter.api.Assertions.*;

class PrefixPluginTest {
    @TempDir Path temporary;

    @Test void independentPrefixPluginsRunAtTheirDeclaredBoundaries() throws Exception {
        var consumed = new AtomicInteger();
        var source = new ByteArrayInputStream(new byte[64]) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                int read = super.read(bytes, offset, length);
                if (read > 0) consumed.addAndGet(read);
                return read;
            }
        };
        var first = prefix("a.prefix", 4, (bytes, claims) -> {
            assertEquals(4, consumed.get());
            assertEquals(4, bytes.length);
            return CheckEvidence.pass("a.prefix");
        });
        var second = prefix("b.prefix", 8, (bytes, claims) -> {
            assertEquals(8, consumed.get());
            assertEquals(8, bytes.length);
            return CheckEvidence.pass("b.prefix");
        });
        try (var gate = gate(first, second, TestChecks.check("z.content", input -> {
            assertEquals(64, input.identity().length());
            return CheckEvidence.pass("z.content");
        })); var result = gate.inspect(source, InputClaims.NONE)) {
            assertEquals(Verdict.APPROVED, result.report().verdict());
            assertEquals(3, result.report().evidence().size());
        }
    }

    @Test void invalidPrefixEvidenceBlocksAndSkipsFullContentInspection() throws Exception {
        var broken = prefix("a.prefix", 4, (bytes, claims) -> CheckEvidence.pass("foreign"));
        try (var gate = gate(broken, TestChecks.check("z.content", input -> {
            fail("Invalid preflight evidence must stop capture");
            return null;
        })); var result = gate.inspect(new ByteArrayInputStream(new byte[64]), InputClaims.NONE)) {
            assertEquals(Verdict.INCOMPLETE, result.report().verdict());
            assertTrue(result.report().reasons().contains(ReasonCode.INVALID_EVIDENCE));
            assertEquals(CheckStatus.NOT_RUN, result.report().evidence().getLast().status());
            assertEquals(0, gate.resourceUsage().reservedStagingBytes());
        }
    }

    @Test void invalidPrefixBudgetFailsBeforeCreatingStaging() throws Exception {
        var broken = prefix("a.prefix", PrefixInspectionCheck.MAX_PREFIX_BYTES + 1,
                (bytes, claims) -> CheckEvidence.pass("a.prefix"));
        assertThrows(IllegalArgumentException.class,
                () -> gate(broken, TestChecks.check("z.content", input -> CheckEvidence.pass("z.content"))));
        try (var files = Files.list(temporary)) { assertEquals(0, files.count()); }
    }

    private FileGate gate(InspectionCheck... checks) throws Exception {
        var installed = List.of(checks);
        var descriptors = installed.stream().map(InspectionCheck::descriptor).toList();
        var policy = CompiledPolicy.compile(new PolicySpec("prefix-test", Use.DOWNLOAD, 128,
                ExecutionMode.EMBEDDED, descriptors.stream().map(CheckDescriptor::id).toList()), descriptors);
        return FileGate.open(temporary, new ResourceLimits(1, 128, 1024), policy, installed);
    }

    private PrefixInspectionCheck prefix(String id, int bytes,
            BiFunction<byte[], InputClaims, CheckEvidence> body) {
        return new PrefixInspectionCheck() {
            @Override public CheckDescriptor descriptor() {
                return new CheckDescriptor(id, "1", "test-only", ExecutionMode.EMBEDDED, CheckKind.PREFLIGHT);
            }
            @Override public int prefixBytes() { return bytes; }
            @Override public CheckEvidence inspectPrefix(byte[] prefix, InputClaims claims) {
                return body.apply(prefix, claims);
            }
        };
    }
}
