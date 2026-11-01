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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Identified package manifest, not semantic document validation or approval evidence. */
public record OfficePackageManifest(Kind kind, String mainPart, OfficePackageInventory inventory,
		Map<String, String> contentTypes, Map<String, List<Relationship>> relationships) {
	public enum Kind { DOCX, XLSX }

	public record Relationship(String id, String type, URI target, boolean external) {
	}

	public OfficePackageManifest {
		contentTypes = Map.copyOf(contentTypes);
		var copy = new HashMap<String, List<Relationship>>();
		relationships.forEach((source, values) -> copy.put(source, List.copyOf(values)));
		relationships = Map.copyOf(copy);
	}
}
