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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.inspection.CheckStatus;
import dev.filegate.core.inspection.ReasonCode;
import dev.filegate.formats.images.internal.ImageDimensions;
import dev.filegate.formats.images.internal.ProfileViolation;
import dev.filegate.formats.images.internal.decode.JdkImageDecoder;
import dev.filegate.formats.images.internal.jpeg.JpegParser;
import dev.filegate.formats.images.internal.png.PngParser;

/**
 * Worker-only format facade; never install the parsers as trusted embedded
 * checks.
 */
public final class RestrictedImageInspector {
	private final ImageProfile profile;
	private final PngParser png;
	private final JpegParser jpeg;
	private final JdkImageDecoder decoder = new JdkImageDecoder();

	public RestrictedImageInspector(ImageProfile profile) {
		this.profile = Objects.requireNonNull(profile, "profile");
		this.png = new PngParser(profile);
		this.jpeg = new JpegParser(profile);
	}

	public CheckEvidence inspect(String checkId, Path captured) {
		try {
			if (Files.size(captured) > profile.maxInputBytes())
				throw new ProfileViolation();
			ImageDimensions dimensions;
			String format;
			try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(captured), 8192))) {
				input.mark(8);
				byte[] prefix = input.readNBytes(8);
				input.reset();
				if (prefix.length == 8 && (prefix[0] & 255) == 137 && prefix[1] == 'P') {
					dimensions = png.inspect(input);
					format = "png";
				} else if (prefix.length >= 2 && (prefix[0] & 255) == 255 && (prefix[1] & 255) == 216) {
					dimensions = jpeg.inspect(input);
					format = "jpeg";
				} else {
					return new CheckEvidence(checkId, CheckStatus.FAIL, ReasonCode.UNSUPPORTED_FORMAT);
				}
			}
			decoder.verify(captured, format, dimensions);
			return CheckEvidence.pass(checkId);
		} catch (ProfileViolation | EOFException definite) {
			return new CheckEvidence(checkId, CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION);
		} catch (IOException | RuntimeException uncertain) {
			return new CheckEvidence(checkId, CheckStatus.ERROR, ReasonCode.CHECK_ERROR);
		}
	}
}
