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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficeManifestReaderTest {
	private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
	private static final String TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
	private static final String RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
	@TempDir
	Path temp;

	private Map<String, String> fixture(boolean spreadsheet) {
		String part = spreadsheet ? "xl/workbook.xml" : "word/document.xml";
		String kind = spreadsheet ? "spreadsheetml.sheet" : "wordprocessingml.document";
		String ns = spreadsheet ? "spreadsheetml" : "wordprocessingml";
		String root = spreadsheet ? "workbook" : "document";
		var parts = new LinkedHashMap<String, String>();
		parts.put("[Content_Types].xml", "<Types xmlns='" + TYPES + "'>"
				+ "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
				+ "<Default Extension='xml' ContentType='application/xml'/>"
				+ "<Override PartName='/" + part + "' ContentType='application/vnd.openxmlformats-officedocument."
				+ kind + ".main+xml'/></Types>");
		parts.put("_rels/.rels", relationships(relationship("rId1", "officeDocument", part, "")));
		parts.put(part, "<" + root + " xmlns='http://schemas.openxmlformats.org/" + ns + "/2006/main'/>");
		return parts;
	}

	private static String relationships(String children) {
		return "<Relationships xmlns='" + RELS + "'>" + children + "</Relationships>";
	}

	private static String relationship(String id, String type, String target, String mode) {
		return "<Relationship Id='" + id + "' Type='" + REL + type + "' Target='" + target + "'"
				+ (mode.isEmpty() ? "" : " TargetMode='" + mode + "'") + "/>";
	}

	private OfficePackageManifest read(Map<String, String> parts, OfficeLinkPolicy links) throws Exception {
		var bytes = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(bytes)) {
			for (var part : parts.entrySet()) {
				zip.putNextEntry(new ZipEntry(part.getKey()));
				zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		Path captured = temp.resolve("captured");
		Files.write(captured, bytes.toByteArray());
		var limits = new OfficePackageLimits(1024 * 1024, 1024 * 1024, 512 * 1024, 128, 32, 10000, 100000);
		return new OfficeManifestReader(limits, links).read(captured);
	}

	private void rejects(Map<String, String> parts) {
		assertThrows(IOException.class, () -> read(parts, OfficeLinkPolicy.HTTPS_ONLY));
	}

	@Test
	void macroFreeDocxAndXlsxMainPartsAreIdentifiedFromStructure() throws Exception {
		assertEquals(OfficePackageManifest.Kind.XLSX, read(fixture(true), OfficeLinkPolicy.NONE).kind());
		var docx = read(fixture(false), OfficeLinkPolicy.NONE);
		assertEquals(OfficePackageManifest.Kind.DOCX, docx.kind());
		assertEquals("word/document.xml", docx.mainPart());
		assertThrows(UnsupportedOperationException.class, () -> docx.relationships().get("").clear());
	}

	@Test
	void declaredTypeMustMatchMainRootAndNamespace() throws Exception {
		var parts = fixture(true);
		parts.put("xl/workbook.xml", "<workbook xmlns='https://untrusted.invalid'/>");
		rejects(parts);
		parts = fixture(true);
		parts.put("[Content_Types].xml", parts.get("[Content_Types].xml")
				.replace("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
						"application/vnd.ms-excel.sheet.macroEnabled.main+xml"));
		rejects(parts);
	}

	@Test
	void rootMainRelationshipMustBeUniqueAndTargetAnExistingPart() throws Exception {
		for (String children : new String[] { "", relationship("r1", "officeDocument", "missing.xml", ""),
				relationship("r1", "officeDocument", "xl/workbook.xml", "")
				+ relationship("r2", "officeDocument", "xl/workbook.xml", "") }) {
			var parts = fixture(true);
			parts.put("_rels/.rels", relationships(children));
			rejects(parts);
		}
	}

	@Test
	void clickableWebLinksRequireExplicitPolicyAndNeverLoadTheirTargets() throws Exception {
		var parts = fixture(false);
		parts.put("word/_rels/document.xml.rels",
				relationships(relationship("link", "hyperlink", "https://example.invalid/not-fetched", "External")));
		var manifest = read(parts, OfficeLinkPolicy.HTTPS_ONLY);
		assertTrue(manifest.relationships().get("word/document.xml").getFirst().external());
		assertThrows(IOException.class, () -> read(parts, OfficeLinkPolicy.NONE));
	}

	@Test
	void externalResourcesDangerousSchemesAndUnknownModesReject() throws Exception {
		for (String link : new String[] {
				relationship("link", "image", "https://example.invalid/image.png", "External"),
				relationship("link", "attachedTemplate", "https://example.invalid/template", "External"),
				relationship("link", "hyperlink", "file:///secret", "External"),
				relationship("link", "hyperlink", "javascript:alert(1)", "External"),
				relationship("link", "hyperlink", "https://user:password@example.invalid/", "External"),
				relationship("link", "hyperlink", "https://example.invalid/", "external") }) {
			var parts = fixture(false);
			parts.put("word/_rels/document.xml.rels", relationships(link));
			rejects(parts);
		}
	}

	@Test
	void internalRelativeTargetsResolveWithinPackageAndCannotEscapeIt() throws Exception {
		var parts = fixture(true);
		parts.put("xl/worksheets/sheet1.xml", "<worksheet/>");
		parts.put("xl/styles.xml", "<styleSheet/>");
		parts.put("xl/worksheets/_rels/sheet1.xml.rels", relationships(relationship("styles", "styles", "../styles.xml", "")));
		assertEquals("/xl/styles.xml", read(parts, OfficeLinkPolicy.NONE).relationships()
				.get("xl/worksheets/sheet1.xml").getFirst().target().getPath());
		for (String target : new String[] { "../../../../xl/styles.xml", "//example.invalid/a", "%2e%2e/styles.xml", "../missing.xml" }) {
			parts.put("xl/worksheets/_rels/sheet1.xml.rels", relationships(relationship("styles", "styles", target, "")));
			rejects(parts);
		}
	}

	@Test
	void undeclaredMissingAndAmbiguousTypesOrRelationshipIdsReject() throws Exception {
		var parts = fixture(true);
		parts.put("[Content_Types].xml", parts.get("[Content_Types].xml").replace("</Types>",
				"<Override PartName='/missing.xml' ContentType='application/xml'/></Types>"));
		rejects(parts);
		parts = fixture(true);
		parts.remove("[Content_Types].xml");
		rejects(parts);
		parts = fixture(true);
		parts.put("_rels/.rels", relationships(relationship("same", "officeDocument", "xl/workbook.xml", "")
				+ relationship("same", "styles", "xl/workbook.xml", "")));
		rejects(parts);
	}

	@Test
	void manifestNamespacesAndUnknownNestedOrNamespacedAttributesReject() throws Exception {
		for (String xml : new String[] { "<Relationships/>",
				"<Relationships xmlns='" + RELS + "'><Unknown/></Relationships>",
				"<Relationships xmlns='" + RELS + "' xmlns:x='urn:extra'><Relationship x:Id='r1' Type='"
				+ REL + "officeDocument' Target='xl/workbook.xml'/></Relationships>" }) {
			var parts = fixture(true);
			parts.put("_rels/.rels", xml);
			rejects(parts);
		}
	}
	@Test
	void relationshipContentTypesAndSourceLocationsCannotHideAlternativeViews() throws Exception {
		var parts = fixture(true);
		parts.put("xl/hidden.xml", relationships(relationship("link", "image", "https://example.invalid/", "External")));
		parts.put("[Content_Types].xml", parts.get("[Content_Types].xml").replace("</Types>",
				"<Override PartName='/xl/hidden.xml' ContentType='application/vnd.openxmlformats-package.relationships+xml'/></Types>"));
		rejects(parts);
		parts = fixture(true);
		parts.put("_rels/xl/workbook.xml.rels", relationships(""));
		rejects(parts);
	}

}
