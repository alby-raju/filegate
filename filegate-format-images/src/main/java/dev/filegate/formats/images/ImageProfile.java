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

/** All numeric limits are explicit deployment inputs, not recommended production defaults. */
public record ImageProfile(
        long maxInputBytes,
        long maxPixels,
        long maxDecodedBytes,
        long maxMetadataBytes,
        int maxSegments) {
    public ImageProfile {
        if (maxInputBytes <= 0
                || maxPixels <= 0
                || maxDecodedBytes <= 0
                || maxMetadataBytes < 0
                || maxSegments <= 0
                || maxSegments > 1_000_000) {
            throw new IllegalArgumentException("Invalid image coverage envelope");
        }
    }
}
