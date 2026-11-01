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

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import dev.filegate.formats.images.ImageProfile;
import dev.filegate.formats.images.internal.ProfileViolation;

/**
 * Bounded zlib and scanline postconditions, including exact stream termination.
 */
final class PngScanlines implements AutoCloseable {
	private final Inflater inflater;
	private final byte[] decoded = new byte[8192];
	private final long rowBytes;
	private final long expected;
	private long produced;

	PngScanlines(ImageProfile profile, int width, int height, int channels) throws ProfileViolation {
		rowBytes = (long) width * channels + 1;
		if (rowBytes > profile.maxDecodedBytes() / height)
			throw new ProfileViolation();
		expected = rowBytes * height;
		inflater = new Inflater();
	}

	void accept(byte[] bytes, int length) throws ProfileViolation {
		if (inflater.finished())
			throw new ProfileViolation();
		inflater.setInput(bytes, 0, length);
		try {
			while (true) {
				int read = inflater.inflate(decoded);
				if (read > expected - produced)
					throw new ProfileViolation();
				for (int i = 0; i < read; i++)
					if ((produced + i) % rowBytes == 0 && (decoded[i] & 255) > 4)
						throw new ProfileViolation();
				produced += read;
				if (inflater.finished()) {
					if (inflater.getRemaining() != 0)
						throw new ProfileViolation();
					break;
				}
				if (inflater.needsDictionary())
					throw new ProfileViolation();
				if (read == 0) {
					if (inflater.needsInput())
						break;
					throw new ProfileViolation();
				}
			}
		} catch (DataFormatException invalid) {
			throw new ProfileViolation();
		}
	}

	boolean complete() {
		return inflater.finished() && produced == expected;
	}

	@Override
	public void close() {
		inflater.end();
	}
}
