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

import java.util.Map;
import java.util.Objects;

/** Validated container inventory, not DOCX/XLSX acceptance or approval evidence. */
public record OfficePackageInventory(Map<String, Part> parts, long expandedBytes) {
	public OfficePackageInventory {
		parts = Map.copyOf(parts);
		if (expandedBytes < 0)
			throw new IllegalArgumentException("Negative expanded length");
	}

	public record Part(long expandedBytes, long crc32, String rootNamespace, String rootName) {
		public Part {
			Objects.requireNonNull(rootNamespace, "rootNamespace");
			Objects.requireNonNull(rootName, "rootName");
		}
	}
}
