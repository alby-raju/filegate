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

package dev.filegate.formats.images;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Worker-only profile implementation. Never register this parser as an embedded core check. */
public final class RestrictedImageInspector {
    private final ImageProfile profile;

    public RestrictedImageInspector(ImageProfile profile) { this.profile = java.util.Objects.requireNonNull(profile); }

    public CheckEvidence inspect(String checkId, Path captured) {
        try {
            if (Files.size(captured) > profile.maxInputBytes()) throw violation();
            Dimensions dimensions;
            String format;
            try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(captured), 8192))) {
                input.mark(8);
                byte[] prefix = input.readNBytes(8);
                input.reset();
                if (prefix.length == 8 && (prefix[0] & 255) == 137 && prefix[1] == 'P') {
                    dimensions = png(input);
                    format = "png";
                } else if (prefix.length >= 2 && (prefix[0] & 255) == 255 && (prefix[1] & 255) == 216) {
                    dimensions = jpeg(input);
                    format = "jpeg";
                } else {
                    return new CheckEvidence(checkId, CheckStatus.FAIL, ReasonCode.UNSUPPORTED_FORMAT);
                }
            }
            decode(captured, format, dimensions);
            return CheckEvidence.pass(checkId);
        } catch (ProfileViolation | EOFException definite) {
            return new CheckEvidence(checkId, CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION);
        } catch (IOException | RuntimeException uncertain) {
            return new CheckEvidence(checkId, CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
        }
    }

    private Dimensions png(DataInputStream input) throws IOException {
        if (!Arrays.equals(input.readNBytes(8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) throw violation();
        Dimensions dimensions = null;
        InflatedScanlines scanlines = null;
        boolean data = false;
        boolean dataEnded = false;
        int chunks = 0;
        long metadata = 0;
        var seen = new HashSet<String>();
        byte[] buffer = new byte[8192];
        try {
            while (true) {
                if (++chunks > profile.maxSegments()) throw violation();
                int length = input.readInt();
                if (length < 0) throw violation();
                byte[] typeBytes = input.readNBytes(4);
                if (typeBytes.length != 4) throw new EOFException();
                String type = new String(typeBytes, StandardCharsets.US_ASCII);
                if (chunks == 1 && !type.equals("IHDR")) throw violation();
                if (!java.util.Set.of("IHDR", "IDAT", "IEND", "sRGB", "pHYs").contains(type)) throw violation();
                if (!type.equals("IDAT") && !seen.add(type)) throw violation();
                var crc = new CRC32();
                crc.update(typeBytes);
                if (type.equals("IHDR")) {
                    if (length != 13 || chunks != 1) throw violation();
                    byte[] header = input.readNBytes(13);
                    if (header.length != 13) throw new EOFException();
                    crc.update(header);
                    var fields = java.nio.ByteBuffer.wrap(header);
                    int width = fields.getInt();
                    int height = fields.getInt();
                    int depth = fields.get() & 255;
                    int color = fields.get() & 255;
                    if (depth != 8 || (color != 2 && color != 6) || fields.get() != 0 || fields.get() != 0 || fields.get() != 0) throw violation();
                    dimensions = dimensions(width, height);
                    scanlines = new InflatedScanlines(width, height, color == 2 ? 3 : 4);
                } else if (type.equals("IDAT")) {
                    if (dataEnded || scanlines == null) throw violation();
                    data = true;
                    for (int left = length; left > 0;) {
                        int read = Math.min(left, buffer.length);
                        input.readFully(buffer, 0, read);
                        crc.update(buffer, 0, read);
                        scanlines.accept(buffer, read);
                        left -= read;
                    }
                } else if (type.equals("IEND")) {
                    if (length != 0 || !data || scanlines == null || !scanlines.complete()) throw violation();
                } else {
                    if (data) { dataEnded = true; throw violation(); }
                    int expected = type.equals("sRGB") ? 1 : 9;
                    if (length != expected || length > profile.maxMetadataBytes() - metadata) throw violation();
                    metadata += length;
                    byte[] bytes = input.readNBytes(expected);
                    if (bytes.length != expected) throw new EOFException();
                    crc.update(bytes);
                    if (type.equals("sRGB") && (bytes[0] & 255) > 3) throw violation();
                    if (type.equals("pHYs") && (bytes[8] & 255) > 1) throw violation();
                }
                if (Integer.toUnsignedLong(input.readInt()) != crc.getValue()) throw violation();
                if (type.equals("IEND")) {
                    if (input.read() != -1) throw violation();
                    return dimensions;
                }
            }
        } finally { if (scanlines != null) scanlines.close(); }
    }

    private Dimensions jpeg(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != 0xffd8) throw violation();
        Dimensions dimensions = null;
        int components = 0;
        var componentIds = new HashSet<Integer>();
        boolean quantization = false;
        boolean huffman = false;
        boolean restartAllowed = false;
        boolean jfif = false;
        long metadata = 0;
        for (int segment = 0; segment < profile.maxSegments(); segment++) {
            if (input.readUnsignedByte() != 255) throw violation();
            int marker;
            do { marker = input.readUnsignedByte(); } while (marker == 255);
            if (!java.util.Set.of(0xc0, 0xc4, 0xdb, 0xdd, 0xe0, 0xda).contains(marker)) throw violation();
            int length = input.readUnsignedShort() - 2;
            if (length < 0) throw violation();
            byte[] bytes = input.readNBytes(length); // JPEG's 16-bit segment length bounds this allocation.
            if (bytes.length != length) throw new EOFException();
            if (marker == 0xc0) {
                if (dimensions != null || length < 6 || bytes[0] != 8) throw violation();
                components = bytes[5] & 255;
                if ((components != 1 && components != 3) || length != 6 + components * 3) throw violation();
                dimensions = dimensions(unsignedShort(bytes, 3), unsignedShort(bytes, 1));
                int blocks = 0;
                for (int i = 0; i < components; i++) {
                    int offset = 6 + i * 3;
                    int h = (bytes[offset + 1] & 255) >>> 4;
                    int v = bytes[offset + 1] & 15;
                    if (!componentIds.add(bytes[offset] & 255) || h < 1 || h > 4 || v < 1 || v > 4 || (bytes[offset + 2] & 255) > 3) throw violation();
                    blocks += h * v;
                }
                if (blocks > 10) throw violation();
            } else if (marker == 0xdb) {
                if (length == 0 || length % 65 != 0) throw violation();
                for (int offset = 0; offset < length; offset += 65) {
                    if ((bytes[offset] & 255) > 3) throw violation(); // 8-bit baseline quantization only.
                    for (int i = 1; i <= 64; i++) if (bytes[offset + i] == 0) throw violation();
                }
                quantization = true;
            } else if (marker == 0xc4) {
                int offset = 0;
                while (offset < length) {
                    if (length - offset < 17 || (bytes[offset] & 255) > 0x13 || (bytes[offset] & 15) > 3) throw violation();
                    int values = 0;
                    for (int i = 1; i <= 16; i++) values += bytes[offset + i] & 255;
                    if (values < 1 || values > 256 || values > length - offset - 17) throw violation();
                    offset += 17 + values;
                }
                if (length == 0) throw violation();
                huffman = true;
            } else if (marker == 0xdd) {
                if (length != 2) throw violation();
                restartAllowed = unsignedShort(bytes, 0) > 0;
            } else if (marker == 0xe0) {
                if (jfif || length < 14 || length > profile.maxMetadataBytes() - metadata
                        || !Arrays.equals(Arrays.copyOf(bytes, 5), new byte[] {'J', 'F', 'I', 'F', 0})
                        || bytes[5] != 1 || (bytes[6] & 255) > 2 || (bytes[7] & 255) > 2
                        || length != 14 + 3 * (bytes[12] & 255) * (bytes[13] & 255)) throw violation();
                metadata += length;
                jfif = true;
            } else {
                if (dimensions == null || !quantization || !huffman || length != 4 + 2 * components || (bytes[0] & 255) != components
                        || bytes[length - 3] != 0 || bytes[length - 2] != 63 || bytes[length - 1] != 0) throw violation();
                var scanIds = new HashSet<Integer>();
                for (int i = 0; i < components; i++) {
                    int id = bytes[1 + i * 2] & 255;
                    int tables = bytes[2 + i * 2] & 255;
                    if (!componentIds.contains(id) || !scanIds.add(id) || (tables >>> 4) > 3 || (tables & 15) > 3) throw violation();
                }
                int restart = 0;
                while (true) {
                    int value = input.readUnsignedByte();
                    if (value != 255) continue;
                    do { value = input.readUnsignedByte(); } while (value == 255);
                    if (value == 0) continue;
                    if (value >= 0xd0 && value <= 0xd7 && restartAllowed && value == 0xd0 + restart) {
                        restart = (restart + 1) % 8;
                        continue;
                    }
                    if (value != 0xd9 || input.read() != -1) throw violation();
                    return dimensions;
                }
            }
        }
        throw violation();
    }

    private Dimensions dimensions(int width, int height) throws ProfileViolation {
        if (width <= 0 || height <= 0 || (long) width * height > profile.maxPixels()
                || (long) width * height > profile.maxDecodedBytes() / 4) throw violation();
        return new Dimensions(width, height);
    }

    private void decode(Path path, String format, Dimensions dimensions) throws IOException {
        ImageReader selected = null;
        var readers = ImageIO.getImageReadersByFormatName(format);
        String provider = format.equals("png") ? "com.sun.imageio.plugins.png.PNGImageReaderSpi" : "com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi";
        while (readers.hasNext()) {
            ImageReader candidate = readers.next();
            if (candidate.getOriginatingProvider().getClass().getName().equals(provider)) { selected = candidate; break; }
            candidate.dispose();
        }
        if (selected == null) throw new IOException("Required pinned JDK image reader is unavailable");
        var warning = new AtomicBoolean();
        try (var input = new FileImageInputStream(path.toFile())) {
            selected.setInput(input, true, true);
            selected.addIIOReadWarningListener((reader, message) -> warning.set(true));
            if (selected.getWidth(0) != dimensions.width || selected.getHeight(0) != dimensions.height) throw violation();
            var decoded = selected.read(0);
            if (decoded == null || warning.get() || decoded.getWidth() != dimensions.width || decoded.getHeight() != dimensions.height) throw violation();
            decoded.flush();
        } finally { selected.dispose(); }
    }

    private static int unsignedShort(byte[] bytes, int offset) { return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255); }
    private record Dimensions(int width, int height) {}
    private static ProfileViolation violation() { return new ProfileViolation(); }
    private static final class ProfileViolation extends IOException { private static final long serialVersionUID = 1L; }

    private final class InflatedScanlines implements AutoCloseable {
        private final Inflater inflater;
        private final byte[] decoded = new byte[8192];
        private final long rowBytes;
        private final long expected;
        private long produced;

        InflatedScanlines(int width, int height, int channels) throws ProfileViolation {
            rowBytes = (long) width * channels + 1;
            if (rowBytes > profile.maxDecodedBytes() / height) throw violation();
            expected = rowBytes * height;
            inflater = new Inflater();
        }

        void accept(byte[] bytes, int length) throws ProfileViolation {
            if (inflater.finished()) throw violation();
            inflater.setInput(bytes, 0, length);
            try {
                while (true) {
                    int read = inflater.inflate(decoded);
                    if (read > expected - produced) throw violation();
                    for (int i = 0; i < read; i++) if ((produced + i) % rowBytes == 0 && (decoded[i] & 255) > 4) throw violation();
                    produced += read;
                    if (inflater.finished()) {
                        if (inflater.getRemaining() != 0) throw violation();
                        break;
                    }
                    if (inflater.needsDictionary()) throw violation();
                    if (read == 0) {
                        if (inflater.needsInput()) break;
                        throw violation();
                    }
                }
            } catch (DataFormatException invalid) { throw violation(); }
        }

        boolean complete() { return inflater.finished() && produced == expected; }
        @Override public void close() { inflater.end(); }
    }
}
