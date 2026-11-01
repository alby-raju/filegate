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
package dev.filegate.formats.spreadsheets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.formats.office.OfficeLinkPolicy;
import dev.filegate.formats.office.OfficePackageLimits;

class RestrictedSpreadsheetTest {
	@TempDir
	Path temp;

	static SpreadsheetProfile profile() {
		return new SpreadsheetProfile(new OfficePackageLimits(1024 * 1024, 8 * 1024 * 1024, 2 * 1024 * 1024,
				128, 64, 100000, 2 * 1024 * 1024), new SpreadsheetLimits(8, 1000, 10000, 1000, 100, 8192, 4096, 32767),
				OfficeLinkPolicy.HTTPS_ONLY);
	}

	static byte[] workbook() throws Exception {
		try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = book.createSheet("Sheet1");
			sheet.createRow(0).createCell(0).setCellValue(5);
			sheet.getRow(0).createCell(1).setCellFormula("SUM(A1:A2)");
			sheet.createRow(1).createCell(0).setCellValue(6);
			var rate = book.createName();
			rate.setNameName("Rate");
			rate.setRefersToFormula("Sheet1!$A$1");
			sheet.getRow(1).createCell(1).setCellFormula("Rate*2");
			var string = sheet.createRow(2).createCell(0);
			string.setCellValue("Ordinary text [book.xlsx] WEBSERVICE");
			var link = book.getCreationHelper().createHyperlink(HyperlinkType.URL);
			link.setAddress("https://example.invalid/not-fetched");
			string.setHyperlink(link);
			var style = book.createCellStyle();
			style.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
			sheet.getRow(0).getCell(0).setCellStyle(style);
			book.createSheet("Hidden");
			book.setSheetVisibility(1, SheetVisibility.HIDDEN);
			book.write(output);
			return output.toByteArray();
		}
	}

	private SpreadsheetReport inspect(byte[] bytes, SpreadsheetProfile profile) throws Exception {
		Path file = temp.resolve("captured.xlsx");
		Files.write(file, bytes);
		var result = new RestrictedSpreadsheetInspector(profile).inspect(file);
		assertArrayEquals(bytes, Files.readAllBytes(file));
		return result;
	}

	private byte[] rewrite(byte[] original, String part, UnaryOperator<String> change) throws Exception {
		var parts = new LinkedHashMap<String, byte[]>();
		try (var zip = new ZipInputStream(new ByteArrayInputStream(original))) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;)
				parts.put(entry.getName(), zip.readAllBytes());
		}
		parts.put(part, change.apply(new String(parts.get(part), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
		var bytes = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(bytes)) {
			for (var entry : parts.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	private void blocked(byte[] bytes) {
		assertThrows(IOException.class, () -> inspect(bytes, profile()));
	}

	@Test
	void basicWorkbookHiddenSheetNamesFormulasStylesAndClickableLinksPassWithoutChangingBytes() throws Exception {
		var report = inspect(workbook(), profile());
		assertEquals(2, report.sheets());
		assertEquals(5, report.cells());
		assertEquals(3, report.formulas());
		assertEquals(1, report.definedNames());
	}

	@Test
	void rawFormulasAndUnusedDefinedNamesCannotHideNetworkFunctions() throws Exception {
		byte[] bytes = workbook();
		blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("SUM(A1:A2)", "WEBSERVICE(&quot;https://example.invalid&quot;)")));
		blocked(rewrite(bytes, "xl/workbook.xml", xml -> xml.replace("Sheet1!$A$1", "INDIRECT(&quot;A1&quot;)")));
	}

	@Test
	void unknownExtensionsConditionalFormulasDrawingsAndArrayFormulasBlock() throws Exception {
		byte[] bytes = workbook();
		for (String feature : new String[] { "<extLst/>", "<drawing r:id='unknown'/>", "<conditionalFormatting sqref='A1'/>" })
			blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("</worksheet>", feature + "</worksheet>")));
		blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("<f>", "<f t='array' ref='B1'>")));
	}

	@Test
	void duplicateCellsRowsAndInvalidNumericValuesBlockBeforeModelAmbiguityCanPass() throws Exception {
		byte[] bytes = workbook();
		blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("r=\"B1\"", "r=\"A1\"")));
		blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("<row r=\"2\"", "<row r=\"1\"")));
		blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("<v>5.0</v>", "<v>NaN</v>")));
	}

	@Test
	void workbookGraphCannotOmitOrRetargetItsDeclaredWorksheet() throws Exception {
		byte[] bytes = workbook();
		blocked(rewrite(bytes, "xl/workbook.xml", xml -> xml.replace("name=\"Hidden\"", "name=\"Sheet1\"")));
		blocked(rewrite(bytes, "xl/_rels/workbook.xml.rels", xml -> xml.replace("worksheets/sheet1.xml", "worksheets/missing.xml")));
	}

	@Test
	void worksheetAndStringBudgetsApplyBeforeModelAllocation() throws Exception {
		byte[] bytes = workbook();
		var oneSheet = new SpreadsheetProfile(profile().packageLimits(),
				new SpreadsheetLimits(1, 1000, 10000, 1000, 100, 8192, 4096, 32767), OfficeLinkPolicy.HTTPS_ONLY);
		assertThrows(SpreadsheetViolation.class, () -> inspect(bytes, oneSheet));
		var oneCell = new SpreadsheetProfile(profile().packageLimits(),
				new SpreadsheetLimits(8, 1000, 1, 1000, 100, 8192, 4096, 32767), OfficeLinkPolicy.HTTPS_ONLY);
		assertThrows(SpreadsheetViolation.class, () -> inspect(bytes, oneCell));
		blocked(rewrite(bytes, "xl/sharedStrings.xml", xml -> xml.replace("uniqueCount=\"1\"", "uniqueCount=\"2147483647\"")));
	}

	@Test
	void sharedMastersAndFollowersAreCheckedWithoutTrustingCachedExpansion() throws Exception {
		String data = "<sheetData><row r='1'><c r='A1'><f t='shared' si='0' ref='A1:A2'>1+2</f><v>3</v></c></row>"
				+ "<row r='2'><c r='A2'><f t='shared' si='0'/><v>3</v></c></row></sheetData>";
		byte[] shared = rewrite(workbook(), "xl/worksheets/sheet1.xml", xml -> xml.replaceAll("(?s)<sheetData>.*?</sheetData>", data));
		assertEquals(3, inspect(shared, profile()).formulas());
		blocked(rewrite(shared, "xl/worksheets/sheet1.xml", xml -> xml.replace("si='0'/>", "si='1'/>")));
		blocked(rewrite(shared, "xl/worksheets/sheet1.xml", xml -> xml.replace("1+2", "INDIRECT(&quot;A1&quot;)")));
	}

	@Test
	void clickableLinksCannotBecomeAutomaticResourcesOrUnsafeSchemes() throws Exception {
		byte[] bytes = workbook();
		blocked(rewrite(bytes, "xl/worksheets/_rels/sheet1.xml.rels", xml -> xml.replace("/hyperlink", "/image")));
		blocked(rewrite(bytes, "xl/worksheets/_rels/sheet1.xml.rels", xml -> xml.replace("https://example.invalid/not-fetched", "file:///secret")));
	}
	@Test
	void metadataAndRelationshipAttributesCannotActivateAlternativeInterpretations() throws Exception {
		byte[] bytes = workbook();
		blocked(rewrite(bytes, "xl/worksheets/sheet1.xml", xml -> xml.replace("</worksheet>",
				"<pageSetup r:id='rId1'/></worksheet>")));
		blocked(rewrite(bytes, "xl/workbook.xml", xml -> xml.replace("</workbook>",
				"<webPublishing autoRepublish='true'/></workbook>")));
		blocked(rewrite(bytes, "docProps/core.xml", xml -> xml.replace("</cp:coreProperties>",
				"<cp:unsupported>payload</cp:unsupported></cp:coreProperties>")));
	}

}
