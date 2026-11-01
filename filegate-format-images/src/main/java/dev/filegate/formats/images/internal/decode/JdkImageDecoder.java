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

package dev.filegate.formats.images.internal.decode;

import dev.filegate.formats.images.internal.ImageDimensions;
import dev.filegate.formats.images.internal.ProfileViolation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

/** Selects only the named JDK readers and verifies their complete decode result. */
public final class JdkImageDecoder {
    public void verify(Path path, String format, ImageDimensions dimensions) throws IOException {
        ImageReader selected = null;
        var readers = ImageIO.getImageReadersByFormatName(format);
        String provider =
                format.equals("png")
                        ? "com.sun.imageio.plugins.png.PNGImageReaderSpi"
                        : "com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi";
        while (readers.hasNext()) {
            ImageReader candidate = readers.next();
            if (candidate.getOriginatingProvider().getClass().getName().equals(provider)) {
                selected = candidate;
                break;
            }
            candidate.dispose();
        }
        if (selected == null)
            throw new IOException("Required pinned JDK image reader is unavailable");
        var warning = new AtomicBoolean();
        try (var input = new FileImageInputStream(path.toFile())) {
            selected.setInput(input, true, true);
            selected.addIIOReadWarningListener((reader, message) -> warning.set(true));
            if (selected.getWidth(0) != dimensions.width()
                    || selected.getHeight(0) != dimensions.height()) throw new ProfileViolation();
            var decoded = selected.read(0);
            if (decoded == null) throw new ProfileViolation();
            try {
                if (warning.get()
                        || decoded.getWidth() != dimensions.width()
                        || decoded.getHeight() != dimensions.height()) throw new ProfileViolation();
            } finally {
                decoded.flush();
            }
        } finally {
            selected.dispose();
        }
    }
}
