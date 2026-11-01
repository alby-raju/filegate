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
package dev.filegate.formats.images.internal.png;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

import dev.filegate.formats.images.ImageProfile;
import dev.filegate.formats.images.internal.ImageDimensions;
import dev.filegate.formats.images.internal.ProfileViolation;

/** Static non-interlaced RGB/RGBA PNG structure validation. */
public final class PngParser {
	private static final Set<String> SUPPORTED_CHUNKS = Set.of("IHDR", "IDAT", "IEND", "sRGB", "pHYs");
	private final ImageProfile profile;

	public PngParser(ImageProfile profile) {
		this.profile = profile;
	}

	public ImageDimensions inspect(DataInputStream input) throws IOException {
		if (!Arrays.equals(input.readNBytes(8), new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 }))
			throw new ProfileViolation();
		ImageDimensions dimensions = null;
		PngScanlines scanlines = null;
		boolean data = false;
		int chunks = 0;
		long metadata = 0;
		var seen = new HashSet<String>();
		byte[] buffer = new byte[8192];
		try {
			while (true) {
				if (++chunks > profile.maxSegments())
					throw new ProfileViolation();
				int length = input.readInt();
				if (length < 0)
					throw new ProfileViolation();
				byte[] typeBytes = input.readNBytes(4);
				if (typeBytes.length != 4)
					throw new EOFException();
				String type = new String(typeBytes, StandardCharsets.US_ASCII);
				if (chunks == 1 && !type.equals("IHDR"))
					throw new ProfileViolation();
				if (!SUPPORTED_CHUNKS.contains(type))
					throw new ProfileViolation();
				if (!type.equals("IDAT") && !seen.add(type))
					throw new ProfileViolation();
				var crc = new CRC32();
				crc.update(typeBytes);
				if (type.equals("IHDR")) {
					Header header = readHeader(input, crc, length, chunks);
					dimensions = header.dimensions();
					scanlines = new PngScanlines(profile, dimensions.width(), dimensions.height(), header.channels());
				} else if (type.equals("IDAT")) {
					if (scanlines == null)
						throw new ProfileViolation();
					data = true;
					readImageData(input, crc, scanlines, length, buffer);
				} else if (type.equals("IEND")) {
					if (length != 0 || !data || scanlines == null || !scanlines.complete())
						throw new ProfileViolation();
				} else {
					if (data)
						throw new ProfileViolation();
					metadata = readMetadata(input, crc, type, length, metadata);
				}
				if (Integer.toUnsignedLong(input.readInt()) != crc.getValue())
					throw new ProfileViolation();
				if (type.equals("IEND")) {
					if (input.read() != -1)
						throw new ProfileViolation();
					return dimensions;
				}
			}
		} finally {
			if (scanlines != null)
				scanlines.close();
		}
	}

	private record Header(ImageDimensions dimensions, int channels) {
	}

	private Header readHeader(DataInputStream input, CRC32 crc, int length, int position) throws IOException {
		if (length != 13 || position != 1)
			throw new ProfileViolation();
		byte[] header = input.readNBytes(13);
		if (header.length != 13)
			throw new EOFException();
		crc.update(header);
		var fields = java.nio.ByteBuffer.wrap(header);
		int width = fields.getInt();
		int height = fields.getInt();
		int depth = fields.get() & 255;
		int color = fields.get() & 255;
		if (depth != 8 || (color != 2 && color != 6) || fields.get() != 0 || fields.get() != 0 || fields.get() != 0)
			throw new ProfileViolation();
		return new Header(ImageDimensions.checked(profile, width, height), color == 2 ? 3 : 4);
	}

	private static void readImageData(DataInputStream input, CRC32 crc, PngScanlines scanlines, int length,
			byte[] buffer) throws IOException {
		for (int left = length; left > 0;) {
			int read = Math.min(left, buffer.length);
			input.readFully(buffer, 0, read);
			crc.update(buffer, 0, read);
			scanlines.accept(buffer, read);
			left -= read;
		}
	}

	private long readMetadata(DataInputStream input, CRC32 crc, String type, int length, long consumed)
			throws IOException {
		int expected = type.equals("sRGB") ? 1 : 9;
		if (length != expected || length > profile.maxMetadataBytes() - consumed)
			throw new ProfileViolation();
		byte[] bytes = input.readNBytes(expected);
		if (bytes.length != expected)
			throw new EOFException();
		crc.update(bytes);
		if (type.equals("sRGB") && (bytes[0] & 255) > 3)
			throw new ProfileViolation();
		if (type.equals("pHYs") && (bytes[8] & 255) > 1)
			throw new ProfileViolation();
		return consumed + length;
	}
}
