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

package dev.filegate.worker.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.UUID;

import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;

/** Versioned bounded binary frames. Never accepts Java object serialization or free-form diagnostics. */
public final class WorkerProtocol {
    public static final int MAX_CHECKS = 64;
    public static final int MAX_FRAME_BYTES = 32 * 1024;
    private static final int MAGIC_V1 = 0x46470101;
    private static final int REQUEST = 1;
    private static final int RESPONSE = 2;
    private WorkerProtocol() {}

    public static void writeRequest(OutputStream output, ProtocolBinding binding) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var data = new DataOutputStream(bytes);
        header(data, REQUEST);
        writeBinding(data, binding);
        writeFrame(output, bytes.toByteArray());
    }

    public static ProtocolBinding readRequest(InputStream input) throws IOException {
        var data = readFrame(input, REQUEST);
        try {
            var binding = readBinding(data);
            requireEnd(data);
            return binding;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid worker request");
        }
    }

    public static void writeResponse(OutputStream output, WorkerResponse response) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var data = new DataOutputStream(bytes);
        header(data, RESPONSE);
        writeBinding(data, response.binding());
        data.writeInt(response.evidence().size());
        for (CheckEvidence evidence : response.evidence()) {
            data.writeUTF(evidence.checkId());
            data.writeByte(evidence.status().ordinal());
            data.writeUTF(evidence.reason().name());
        }
        writeFrame(output, bytes.toByteArray());
    }

    public static WorkerResponse readResponse(InputStream input, ProtocolBinding expected) throws IOException {
        var data = readFrame(input, RESPONSE);
        try {
            var binding = readBinding(data);
            int count = boundedCount(data.readInt());
            var evidence = new ArrayList<CheckEvidence>(count);
            for (int i = 0; i < count; i++) {
                String id = data.readUTF();
                int status = data.readUnsignedByte();
                if (status >= CheckStatus.values().length) throw new IOException("Unknown check status");
                evidence.add(new CheckEvidence(id, CheckStatus.values()[status], ReasonCode.valueOf(data.readUTF())));
            }
            requireEnd(data);
            var response = new WorkerResponse(binding, evidence);
            response.validateFor(expected);
            return response;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid worker response");
        }
    }

    private static void header(DataOutputStream output, int kind) throws IOException {
        output.writeInt(MAGIC_V1);
        output.writeByte(kind);
    }

    private static void writeBinding(DataOutputStream output, ProtocolBinding binding) throws IOException {
        output.writeLong(binding.requestId().getMostSignificantBits());
        output.writeLong(binding.requestId().getLeastSignificantBits());
        output.writeLong(binding.artifact().length());
        output.write(HexFormat.of().parseHex(binding.artifact().sha256()));
        output.write(HexFormat.of().parseHex(binding.policyFingerprint()));
        output.writeUTF(binding.inspectorBuild());
        output.writeInt(binding.expectedChecks().size());
        for (String check : binding.expectedChecks()) output.writeUTF(check);
    }

    private static ProtocolBinding readBinding(DataInputStream input) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        long length = input.readLong();
        byte[] digest = new byte[32];
        byte[] policy = new byte[32];
        input.readFully(digest);
        input.readFully(policy);
        String build = input.readUTF();
        int count = boundedCount(input.readInt());
        var checks = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) checks.add(input.readUTF());
        return new ProtocolBinding(id, new ArtifactIdentity(length, HexFormat.of().formatHex(digest)),
                HexFormat.of().formatHex(policy), build, checks);
    }

    private static int boundedCount(int count) throws IOException {
        if (count < 0 || count > WorkerProtocol.MAX_CHECKS) throw new IOException("Invalid check count");
        return count;
    }

    private static void writeFrame(OutputStream output, byte[] frame) throws IOException {
        if (frame.length > MAX_FRAME_BYTES) throw new IOException("Frame limit exceeded");
        var data = new DataOutputStream(output);
        data.writeInt(frame.length);
        data.write(frame);
        data.flush();
    }

    private static DataInputStream readFrame(InputStream input, int expectedKind) throws IOException {
        var source = new DataInputStream(input);
        int length = source.readInt();
        if (length < 5 || length > MAX_FRAME_BYTES) throw new IOException("Frame limit exceeded");
        byte[] frame = new byte[length];
        source.readFully(frame);
        var data = new DataInputStream(new ByteArrayInputStream(frame));
        if (data.readInt() != MAGIC_V1 || data.readUnsignedByte() != expectedKind) {
            throw new IOException("Unknown protocol version or message direction");
        }
        return data;
    }

    private static void requireEnd(DataInputStream data) throws IOException {
        if (data.read() != -1) throw new IOException("Trailing frame data");
    }
}
