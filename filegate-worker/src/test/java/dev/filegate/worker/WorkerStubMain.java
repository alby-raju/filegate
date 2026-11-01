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

import dev.filegate.core.CheckEvidence;

/** Transport fault fixture only. It does not inspect content. */
public final class WorkerStubMain {
    public static void main(String[] args) throws Exception {
        boolean sandbox = args[0].startsWith("sandbox-");
        if (sandbox) LinuxContainmentProbe.verify(SandboxLimits.parse(args, 1));
        var request = WorkerProtocol.readRequest(System.in);
        if (sandbox) {
            if (request.artifact().length() > 1024) throw new IllegalArgumentException("Fixture payload limit");
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            long length = 0;
            byte[] buffer = new byte[128];
            for (int read; (read = System.in.read(buffer)) != -1;) {
                length += read;
                if (length > request.artifact().length()) throw new java.io.IOException("Wrong payload length");
                digest.update(buffer, 0, read);
            }
            if (length != request.artifact().length()
                    || !java.util.HexFormat.of().formatHex(digest.digest()).equals(request.artifact().sha256())) {
                throw new java.io.IOException("Wrong payload identity");
            }
        }
        switch (args[0]) {
            case "crash" -> System.exit(7);
            case "eof-crash" -> {
                System.out.close();
                Thread.sleep(50);
                System.exit(7);
            }
            case "hang", "sandbox-hang" -> Thread.sleep(60_000);
            case "stderr" -> {
                System.err.write(new byte[2048]);
                System.err.flush();
                Thread.sleep(60_000);
            }
            case "bad" -> {
                System.out.write(new byte[] {0, 0, 0, 0});
                System.out.flush();
            }
            default -> {
                WorkerProtocol.writeResponse(System.out, new WorkerResponse(request,
                        request.expectedChecks().stream().map(CheckEvidence::pass).toList()));
                if (args[0].equals("trailing")) { System.out.write(42); System.out.flush(); }
                if (args[0].equals("late-exit")) Thread.sleep(60_000);
            }
        }
    }
}
