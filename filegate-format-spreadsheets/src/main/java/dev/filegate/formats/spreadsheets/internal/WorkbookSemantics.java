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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dev.filegate.formats.office.OfficePackageManifest;
import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetReport;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

public final class WorkbookSemantics {
	private WorkbookSemantics() { }

	public static SpreadsheetReport verify(XSSFWorkbook workbook, OfficePackageManifest manifest, SpreadsheetLimits limits)
			throws SpreadsheetViolation {
		int count = workbook.getNumberOfSheets();
		if (count < 1 || count > limits.maxSheets() || workbook.getNumberOfNames() > limits.maxDefinedNames())
			throw new SpreadsheetViolation("Workbook sheet/name allowance exhausted");
		var source = workbook.getSharedStringSource();
		var shared = source == null ? List.<org.apache.poi.ss.usermodel.RichTextString>of() : source.getSharedStringItems();
		if (shared.size() > limits.maxSharedStrings())
			throw new SpreadsheetViolation("Shared-string allowance exhausted");
		for (var string : shared)
			if (string.length() > limits.maxCellCharacters()) throw new SpreadsheetViolation("Shared string exceeds its envelope");
		var links = links(manifest, manifest.mainPart());
		var ids = new HashSet<Long>();
		var names = new HashSet<String>();
		var targets = new HashSet<String>();
		var sheets = workbook.getCTWorkbook().getSheets().getSheetList();
		if (sheets.size() != count)
			throw new SpreadsheetViolation("Workbook sheet views disagree");
		for (var sheet : sheets) {
			var link = links.get(sheet.getId());
			if (sheet.getSheetId() < 1 || !ids.add(sheet.getSheetId()) || sheet.getName().isEmpty()
					|| sheet.getName().length() > 31 || sheet.getName().startsWith("'") || sheet.getName().endsWith("'")
					|| sheet.getName().chars().anyMatch(Character::isISOControl) || sheet.getName().matches(".*[:\\\\/?*\\[\\]].*")
					|| !names.add(sheet.getName().toLowerCase(Locale.ROOT)) || link == null || link.external()
					|| !link.type().equals(SpreadsheetPackageGraph.REL + "worksheet") || !targets.add(link.target().getPath().substring(1)))
				throw new SpreadsheetViolation("Workbook sheet metadata or relationship is invalid");
		}
		long physicalSheets = manifest.contentTypes().values().stream().filter(SpreadsheetPackageGraph.SHEET::equals).count();
		if (physicalSheets != targets.size())
			throw new SpreadsheetViolation("Unlisted worksheet part is unsupported");
		var policy = new FormulaPolicy(workbook, limits);
		var definedNames = new DefinedNames(workbook, policy);
		long rows = 0;
		long cells = 0;
		long formulas = workbook.getNumberOfNames();
		for (int index = 0; index < count; index++) {
			var sheet = workbook.getSheetAt(index);
			String part = sheet.getPackagePart().getPartName().getName().substring(1);
			if (!targets.contains(part))
				throw new SpreadsheetViolation("Worksheet model does not match its package");
			long previousRow = 0;
			var sharedFormulas = new SharedFormulas(policy, limits, index);
			for (var row : sheet.getCTWorksheet().getSheetData().getRowList()) {
				if (++rows > limits.maxRows() || !row.isSetR() || row.getR() <= previousRow || row.getR() > 1048576)
					throw new SpreadsheetViolation("Duplicate, unordered or excessive worksheet rows");
				previousRow = row.getR();
				int previousColumn = -1;
				for (var cell : row.getCList()) {
					if (++cells > limits.maxCells() || !cell.isSetR() || !cell.getR().matches("[A-Z]{1,3}[1-9][0-9]{0,6}"))
						throw new SpreadsheetViolation("Cell reference or allowance is invalid");
					var reference = new CellReference(cell.getR());
					if (reference.getRow() != row.getR() - 1 || reference.getCol() <= previousColumn || reference.getCol() >= 16384)
						throw new SpreadsheetViolation("Duplicate, unordered or out-of-range worksheet cells");
					previousColumn = reference.getCol();
					if (cell.isSetS() && cell.getS() >= workbook.getNumCellStyles())
						throw new SpreadsheetViolation("Cell refers to a missing style");
					var modeled = sheet.getRow(reference.getRow()).getCell(reference.getCol());
					if (cell.isSetV() && cell.getV().length() > limits.maxCellCharacters())
						throw new SpreadsheetViolation("Cached cell value exceeds its envelope");
					if (cell.isSetF()) {
						sharedFormulas.inspect(cell, reference);
						policy.inspect(modeled.getCellFormula(), index, FormulaType.CELL);
						formulas++;
					}
					scalar(modeled, cell, limits);
				}
			}
			if (sheet.getCTWorksheet().isSetHyperlinks()) {
				var sheetLinks = links(manifest, part);
				for (var hyperlink : sheet.getCTWorksheet().getHyperlinks().getHyperlinkList()) {
					var anchor = CellRangeAddress.valueOf(hyperlink.getRef());
					if (anchor.getFirstRow() < 0 || anchor.getFirstColumn() < 0 || anchor.getLastRow() >= 1048576
							|| anchor.getLastColumn() >= 16384)
						throw new SpreadsheetViolation("Hyperlink anchor is outside the worksheet");
					if (hyperlink.isSetId()) {
						var link = sheetLinks.get(hyperlink.getId());
						if (link == null || !link.external() || !link.type().equals(SpreadsheetPackageGraph.REL + "hyperlink")
								|| hyperlink.isSetLocation())
							throw new SpreadsheetViolation("Hyperlink does not match its approved relationship");
					} else {
						definedNames.reference(hyperlink.getLocation(), index);
					}
				}
			}
		}
		return new SpreadsheetReport(count, rows, cells, formulas, workbook.getNumberOfNames());
	}

	private static void scalar(org.apache.poi.xssf.usermodel.XSSFCell modeled,
			org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell cell, SpreadsheetLimits limits)
			throws SpreadsheetViolation {
		if (cell.isSetF() && (!cell.isSetV() || cell.getV().isEmpty())) return;
		CellType type = cell.isSetF() ? modeled.getCachedFormulaResultType() : modeled.getCellType();
		switch (type) {
		case STRING -> {
			if (modeled.getStringCellValue().length() > limits.maxCellCharacters())
				throw new SpreadsheetViolation("Cell string exceeds its envelope");
		}
		case NUMERIC -> {
			if (!Double.isFinite(modeled.getNumericCellValue()))
				throw new SpreadsheetViolation("Nonfinite numeric cell value is unsupported");
		}
		case BOOLEAN -> {
			if (!cell.isSetV() || (!cell.getV().equals("0") && !cell.getV().equals("1")))
				throw new SpreadsheetViolation("Invalid boolean cell value");
		}
		case ERROR -> org.apache.poi.ss.usermodel.FormulaError.forInt(modeled.getErrorCellValue());
		case BLANK -> { }
		default -> throw new SpreadsheetViolation("Unsupported cell value representation");
		}
	}

	private static Map<String, OfficePackageManifest.Relationship> links(OfficePackageManifest manifest, String source) {
		var result = new HashMap<String, OfficePackageManifest.Relationship>();
		for (var link : manifest.relationships().getOrDefault(source, List.of())) result.put(link.id(), link);
		return result;
	}
}
