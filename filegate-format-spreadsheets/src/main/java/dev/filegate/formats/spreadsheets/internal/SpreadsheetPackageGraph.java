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
package dev.filegate.formats.spreadsheets.internal;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import dev.filegate.formats.office.OfficePackageManifest;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Closed part/relationship roles; no hidden or unreferenced payloads enter the workbook model. */
public final class SpreadsheetPackageGraph {
	public static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
	public static final String BOOK = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
	public static final String SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";
	public static final String STYLES = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml";
	public static final String STRINGS = "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml";
	public static final String THEME = "application/vnd.openxmlformats-officedocument.theme+xml";
	public static final String CHAIN = "application/vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml";
	private static final String CORE = "application/vnd.openxmlformats-package.core-properties+xml";
	private static final String EXTENDED = "application/vnd.openxmlformats-officedocument.extended-properties+xml";
	private static final String CUSTOM = "application/vnd.openxmlformats-officedocument.custom-properties+xml";
	private static final String RELS = "application/vnd.openxmlformats-package.relationships+xml";
	private static final Map<String, QName> ROOTS = Map.ofEntries(
			Map.entry(BOOK, new QName(SpreadsheetPreflight.MAIN, "workbook")),
			Map.entry(SHEET, new QName(SpreadsheetPreflight.MAIN, "worksheet")),
			Map.entry(STYLES, new QName(SpreadsheetPreflight.MAIN, "styleSheet")),
			Map.entry(STRINGS, new QName(SpreadsheetPreflight.MAIN, "sst")),
			Map.entry(CHAIN, new QName(SpreadsheetPreflight.MAIN, "calcChain")),
			Map.entry(THEME, new QName("http://schemas.openxmlformats.org/drawingml/2006/main", "theme")),
			Map.entry(CORE, new QName("http://schemas.openxmlformats.org/package/2006/metadata/core-properties", "coreProperties")),
			Map.entry(EXTENDED, new QName("http://schemas.openxmlformats.org/officeDocument/2006/extended-properties", "Properties")),
			Map.entry(CUSTOM, new QName("http://schemas.openxmlformats.org/officeDocument/2006/custom-properties", "Properties")),
			Map.entry(RELS, new QName("http://schemas.openxmlformats.org/package/2006/relationships", "Relationships")));

	private SpreadsheetPackageGraph() {
	}

	public static void verify(OfficePackageManifest manifest) throws SpreadsheetViolation {
		if (manifest.kind() != OfficePackageManifest.Kind.XLSX)
			throw new SpreadsheetViolation("Only the declared basic XLSX profile is supported");
		for (var part : manifest.contentTypes().entrySet()) {
			var root = ROOTS.get(part.getValue());
			var actual = manifest.inventory().parts().get(part.getKey());
			if (root == null || !root.getLocalPart().equals(actual.rootName()) || !root.getNamespaceURI().equals(actual.rootNamespace()))
				throw new SpreadsheetViolation("Unsupported spreadsheet part type or root");
		}
		var seen = new HashSet<String>();
		var pending = new ArrayDeque<String>();
		pending.add("");
		while (!pending.isEmpty()) {
			String source = pending.removeFirst();
			if (!seen.add(source)) continue;
			var singletonRoles = new HashSet<String>();
			for (var link : manifest.relationships().getOrDefault(source, List.of())) {
				if (link.external()) {
					if (!SHEET.equals(manifest.contentTypes().get(source)) || !link.type().equals(REL + "hyperlink"))
						throw new SpreadsheetViolation("Clickable external links are only supported on worksheets");
					continue;
				}
				String target = link.target().getPath().substring(1);
				String targetType = manifest.contentTypes().get(target);
				String expected = role(source, manifest.contentTypes().get(source), link.type());
				if (expected == null || !expected.equals(targetType))
					throw new SpreadsheetViolation("Unsupported spreadsheet relationship role");
				if (!expected.equals(SHEET) && !singletonRoles.add(link.type()))
					throw new SpreadsheetViolation("Duplicate singleton spreadsheet relationship");
				pending.add(target);
			}
		}
		for (var part : manifest.contentTypes().entrySet()) {
			if (!part.getValue().equals(RELS) && !seen.contains(part.getKey()))
				throw new SpreadsheetViolation("Unreferenced spreadsheet payload is unsupported");
		}
		if (!seen.containsAll(manifest.relationships().keySet()))
			throw new SpreadsheetViolation("Unreachable relationship source is unsupported");
	}

	private static String role(String source, String sourceType, String relationship) {
		if (source.isEmpty())
			return Map.of(REL + "officeDocument", BOOK, REL + "extended-properties", EXTENDED, REL + "custom-properties", CUSTOM,
					"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties", CORE).get(relationship);
		if (BOOK.equals(sourceType))
			return Map.of(REL + "worksheet", SHEET, REL + "styles", STYLES, REL + "sharedStrings", STRINGS,
					REL + "theme", THEME, REL + "calcChain", CHAIN).get(relationship);
		return null;
	}
}
