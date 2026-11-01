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

package dev.filegate.core.inspection;

/** Untrusted, bounded metadata. Neither field is used as a staging path. */
public record InputClaims(String filename, String mediaType) {
    public static final InputClaims NONE = new InputClaims("", "");

    public InputClaims {
        if (filename == null || mediaType == null || filename.length() > 256 || mediaType.length() > 128
                || filename.chars().anyMatch(Character::isISOControl)
                || mediaType.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid or unbounded input claims");
        }
    }
}
