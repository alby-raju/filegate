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
package dev.filegate.formats.office;

/** Explicit container limits; these are not deployment defaults. XML counts cover all parts. */
public record OfficePackageLimits(long maxInputBytes, long maxExpandedBytes, long maxPartBytes,
		int maxEntries, int maxXmlDepth, long maxXmlEvents, long maxXmlCharacters) {
	public OfficePackageLimits {
		if (maxInputBytes <= 0 || maxExpandedBytes <= 0 || maxPartBytes <= 0
				|| maxPartBytes > maxExpandedBytes || maxEntries <= 0 || maxEntries > 100_000
				|| maxXmlDepth <= 0 || maxXmlDepth > 256 || maxXmlEvents <= 0 || maxXmlCharacters <= 0)
			throw new IllegalArgumentException("Invalid Office package envelope");
	}
}
