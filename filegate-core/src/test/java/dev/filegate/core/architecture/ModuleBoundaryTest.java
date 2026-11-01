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

package dev.filegate.core.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.filegate.core.FileGate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Compile real clients against the named module, rather than relying on test classpath visibility. */
class ModuleBoundaryTest {
    @TempDir Path temporary;

    @Test
    void externalClientsCanUseThePublicContracts() throws Exception {
        assertEquals(
                0,
                compile(
                        """
                import dev.filegate.core.FileGate;
                import dev.filegate.core.inspection.Inspection;
                import dev.filegate.core.inspection.Approved;
                import dev.filegate.core.policy.Use;
                class Client {
                    Use intendedUse(Inspection result) {
                        return result instanceof Approved approved ? approved.content().use() : null;
                    }
                }
                """));
    }

    @Test
    void externalClientsCannotAccessCaptureOrCapabilityImplementations() throws Exception {
        for (String hidden :
                new String[] {
                    "dev.filegate.core.internal.capture.StageStore",
                    "dev.filegate.core.internal.content.VerifiedContent",
                    "dev.filegate.core.internal.content.VerifiedTransferReceipt"
                }) {
            assertNotEquals(0, compile("import " + hidden + "; class Client {}"), hidden);
        }
    }

    private int compile(String source) throws Exception {
        Path client = temporary.resolve("Client.java");
        Files.writeString(client, source);
        Path module =
                Path.of(FileGate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process compiler =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                                "--release",
                                "21",
                                "--module-path",
                                module.toString(),
                                "--add-modules",
                                "dev.filegate.core",
                                "-d",
                                temporary.toString(),
                                client.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        try {
            assertTrue(compiler.waitFor(15, TimeUnit.SECONDS), "Consumer compilation timed out");
            return compiler.exitValue();
        } finally {
            if (compiler.isAlive()) compiler.destroyForcibly();
        }
    }
}
