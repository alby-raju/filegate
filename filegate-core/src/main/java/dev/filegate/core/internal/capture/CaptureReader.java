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
package dev.filegate.core.internal.capture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HexFormat;

import dev.filegate.core.content.ArtifactIdentity;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.core.internal.crypto.Sha256;

/** Counts and hashes actual bytes with fixed buffers; owns neither stream. */
final class CaptureReader {
	private final int bufferBytes;

	CaptureReader(int bufferBytes) {
		this.bufferBytes = bufferBytes;
	}

	ArtifactIdentity copy(InputStream source, OutputStream destination, long limit, CapturePrefix prefix)
			throws IOException {
		var digest = Sha256.newDigest();
		byte[] buffer = new byte[bufferBytes];
		long count = 0;
		while (true) {
			if (Thread.currentThread().isInterrupted())
				throw new CaptureFailure(ReasonCode.CANCELLED);
			long remaining = limit - count;
			int wanted = (int) (remaining >= buffer.length ? buffer.length : remaining + 1);
			wanted = Math.min(wanted, prefix.readLimit());
			int read = readWithProgress(source, buffer, wanted);
			if (read == -1) {
				prefix.finish();
				return new ArtifactIdentity(count, HexFormat.of().formatHex(digest.digest()));
			}
			if (read > remaining)
				throw new CaptureFailure(ReasonCode.INPUT_TOO_LARGE);
			prefix.accept(buffer, read);
			destination.write(buffer, 0, read);
			digest.update(buffer, 0, read);
			count += read;
		}
	}

	private static int readWithProgress(InputStream source, byte[] buffer, int wanted) throws IOException {
		int read = source.read(buffer, 0, wanted);
		if (read != 0)
			return read;
		int next = source.read();
		if (next == -1)
			return -1;
		buffer[0] = (byte) next;
		return 1;
	}
}
