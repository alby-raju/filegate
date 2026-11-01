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

package dev.filegate.worker.internal.process;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Confirms native process cleanup; OS containment must additionally revoke escaped descendants. */
public final class ProcessTermination {
    private final Duration grace;

    public ProcessTermination(Duration grace) { this.grace = grace; }

    public boolean terminate(Process process) {
        // Best-effort tree cleanup for transport tests. OS containment must control escaped descendants.
        List<ProcessHandle> children;
        boolean descendantsVisible = true;
        try {
            children = process.descendants().toList();
        } catch (RuntimeException unavailable) {
            // Still kill the known process; never certify termination when enumeration was denied.
            children = List.of();
            descendantsVisible = false;
        }
        children.forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        try {
            long stop = System.nanoTime() + grace.toNanos();
            if (!process.waitFor(grace.toNanos(), TimeUnit.NANOSECONDS)) return false;
            for (ProcessHandle child : children) {
                if (child.isAlive()) {
                    long left = stop - System.nanoTime();
                    if (left <= 0) return false;
                    try { child.onExit().get(left, TimeUnit.NANOSECONDS); }
                    catch (ExecutionException | TimeoutException failure) { return false; }
                }
            }
            return descendantsVisible && children.stream().noneMatch(ProcessHandle::isAlive);
        } catch (InterruptedException cancellation) {
            interrupted = true;
            return false;
        } finally {
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }


}
