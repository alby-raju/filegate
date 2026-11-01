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

package dev.filegate.formats.images.internal.jpeg;

import dev.filegate.formats.images.ImageProfile;
import dev.filegate.formats.images.internal.ImageDimensions;
import dev.filegate.formats.images.internal.ProfileViolation;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Baseline sequential JPEG structure, table and scan validation. */
public final class JpegParser {
    private static final int MARKER_PREFIX = 0xff;
    private static final int START_OF_IMAGE = 0xffd8;
    private static final int BASELINE_FRAME = 0xc0;
    private static final int HUFFMAN_TABLE = 0xc4;
    private static final int QUANTIZATION_TABLE = 0xdb;
    private static final int RESTART_INTERVAL = 0xdd;
    private static final int JFIF_SEGMENT = 0xe0;
    private static final int START_OF_SCAN = 0xda;
    private static final int END_OF_IMAGE = 0xd9;
    private static final Set<Integer> SUPPORTED_SEGMENTS = Set.of(
            BASELINE_FRAME, HUFFMAN_TABLE, QUANTIZATION_TABLE, RESTART_INTERVAL, JFIF_SEGMENT, START_OF_SCAN);
    private record Frame(ImageDimensions dimensions, int components, Set<Integer> componentIds) {}

    private final ImageProfile profile;

    public JpegParser(ImageProfile profile) { this.profile = profile; }

    public ImageDimensions inspect(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != START_OF_IMAGE) throw new ProfileViolation();
        Frame frame = null;
        boolean quantization = false;
        boolean huffman = false;
        boolean restartAllowed = false;
        boolean jfif = false;
        long metadata = 0;
        for (int segment = 0; segment < profile.maxSegments(); segment++) {
            int marker = readMarker(input);
            if (!SUPPORTED_SEGMENTS.contains(marker)) throw new ProfileViolation();
            byte[] bytes = readSegment(input);
            switch (marker) {
                case BASELINE_FRAME -> {
                    if (frame != null) throw new ProfileViolation();
                    frame = readFrame(bytes);
                }
                case QUANTIZATION_TABLE -> {
                    validateQuantization(bytes);
                    quantization = true;
                }
                case HUFFMAN_TABLE -> {
                    validateHuffman(bytes);
                    huffman = true;
                }
                case RESTART_INTERVAL -> {
                    if (bytes.length != 2) throw new ProfileViolation();
                    restartAllowed = unsignedShort(bytes, 0) > 0;
                }
                case JFIF_SEGMENT -> {
                    if (jfif) throw new ProfileViolation();
                    validateJfif(bytes, metadata);
                    metadata += bytes.length;
                    jfif = true;
                }
                case START_OF_SCAN -> {
                    if (frame == null || !quantization || !huffman) throw new ProfileViolation();
                    validateScan(frame, bytes);
                    readEntropyToEnd(input, restartAllowed);
                    return frame.dimensions();
                }
                default -> throw new ProfileViolation();
            }
        }
        throw new ProfileViolation();
    }

    private Frame readFrame(byte[] bytes) throws ProfileViolation {
        if (bytes.length < 6 || bytes[0] != 8) throw new ProfileViolation();
        int components = bytes[5] & 255;
        if ((components != 1 && components != 3) || bytes.length != 6 + components * 3) throw new ProfileViolation();
        ImageDimensions dimensions = ImageDimensions.checked(profile, unsignedShort(bytes, 3), unsignedShort(bytes, 1));
        var ids = new HashSet<Integer>();
        int blocks = 0;
        for (int component = 0; component < components; component++) {
            int offset = 6 + component * 3;
            int horizontal = (bytes[offset + 1] & 255) >>> 4;
            int vertical = bytes[offset + 1] & 15;
            if (!ids.add(bytes[offset] & 255) || horizontal < 1 || horizontal > 4 || vertical < 1 || vertical > 4
                    || (bytes[offset + 2] & 255) > 3) throw new ProfileViolation();
            blocks += horizontal * vertical;
        }
        if (blocks > 10) throw new ProfileViolation();
        return new Frame(dimensions, components, Set.copyOf(ids));
    }

    private static void validateQuantization(byte[] bytes) throws ProfileViolation {
        if (bytes.length == 0 || bytes.length % 65 != 0) throw new ProfileViolation();
        for (int offset = 0; offset < bytes.length; offset += 65) {
            if ((bytes[offset] & 255) > 3) throw new ProfileViolation();
            for (int value = 1; value <= 64; value++) if (bytes[offset + value] == 0) throw new ProfileViolation();
        }
    }

    private static void validateHuffman(byte[] bytes) throws ProfileViolation {
        int offset = 0;
        while (offset < bytes.length) {
            if (bytes.length - offset < 17 || (bytes[offset] & 255) > 0x13 || (bytes[offset] & 15) > 3) throw new ProfileViolation();
            int values = 0;
            for (int index = 1; index <= 16; index++) values += bytes[offset + index] & 255;
            if (values < 1 || values > 256 || values > bytes.length - offset - 17) throw new ProfileViolation();
            offset += 17 + values;
        }
        if (bytes.length == 0) throw new ProfileViolation();
    }

    private void validateJfif(byte[] bytes, long metadata) throws ProfileViolation {
        if (bytes.length < 14 || bytes.length > profile.maxMetadataBytes() - metadata
                || !Arrays.equals(Arrays.copyOf(bytes, 5), new byte[] {'J', 'F', 'I', 'F', 0})
                || bytes[5] != 1 || (bytes[6] & 255) > 2 || (bytes[7] & 255) > 2
                || bytes.length != 14 + 3 * (bytes[12] & 255) * (bytes[13] & 255)) throw new ProfileViolation();
    }

    private static void validateScan(Frame frame, byte[] bytes) throws ProfileViolation {
        int length = bytes.length;
        if (length != 4 + 2 * frame.components() || (bytes[0] & 255) != frame.components()
                || bytes[length - 3] != 0 || bytes[length - 2] != 63 || bytes[length - 1] != 0) throw new ProfileViolation();
        var seen = new HashSet<Integer>();
        for (int component = 0; component < frame.components(); component++) {
            int id = bytes[1 + component * 2] & 255;
            int tables = bytes[2 + component * 2] & 255;
            if (!frame.componentIds().contains(id) || !seen.add(id) || (tables >>> 4) > 3 || (tables & 15) > 3) throw new ProfileViolation();
        }
    }

    private static void readEntropyToEnd(DataInputStream input, boolean restartAllowed) throws IOException {
        int restart = 0;
        while (true) {
            int value = input.readUnsignedByte();
            if (value != MARKER_PREFIX) continue;
            do { value = input.readUnsignedByte(); } while (value == MARKER_PREFIX);
            if (value == 0) continue;
            if (value >= 0xd0 && value <= 0xd7 && restartAllowed && value == 0xd0 + restart) {
                restart = (restart + 1) % 8;
                continue;
            }
            if (value != END_OF_IMAGE || input.read() != -1) throw new ProfileViolation();
            return;
        }
    }

    private static int readMarker(DataInputStream input) throws IOException {
        if (input.readUnsignedByte() != MARKER_PREFIX) throw new ProfileViolation();
        int marker;
        do { marker = input.readUnsignedByte(); } while (marker == MARKER_PREFIX);
        return marker;
    }

    private static byte[] readSegment(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort() - 2;
        if (length < 0) throw new ProfileViolation();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        return bytes;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
    }
}
