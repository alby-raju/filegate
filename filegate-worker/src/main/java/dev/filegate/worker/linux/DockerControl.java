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

package dev.filegate.worker.linux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Finite daemon control operations with no inherited credentials or retained diagnostics. */
final class DockerControl {
    private final Duration timeout;

    DockerControl(Duration timeout) { this.timeout = timeout; }

    boolean succeeds(List<String> command) {
        Process process = null;
        boolean interrupted = Thread.interrupted();
        try {
            var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            process = builder.start();
            process.getOutputStream().close();
            return process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS) && process.exitValue() == 0;
        } catch (IOException unavailable) {
            return false;
        } catch (InterruptedException cancelled) {
            interrupted = true;
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException cancelled) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
