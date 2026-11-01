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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import dev.filegate.formats.office.OfficeElementObserver;
import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Bounds semantic objects before the workbook model is allocated. */
public final class SpreadsheetPreflight implements OfficeElementObserver {
	public static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
	private static final Set<String> NAMESPACES = Set.of(MAIN,
			"http://schemas.openxmlformats.org/package/2006/content-types",
			"http://schemas.openxmlformats.org/package/2006/relationships",
			"http://schemas.openxmlformats.org/package/2006/metadata/core-properties",
			"http://schemas.openxmlformats.org/drawingml/2006/main",
			"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties",
			"http://schemas.openxmlformats.org/officeDocument/2006/custom-properties",
			"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes",
			"http://purl.org/dc/elements/1.1/", "http://purl.org/dc/terms/", "http://purl.org/dc/dcmitype/");
	private static final Set<String> ATTRIBUTES = Set.of("", "http://www.w3.org/XML/1998/namespace",
			"http://www.w3.org/2001/XMLSchema-instance",
			"http://schemas.openxmlformats.org/officeDocument/2006/relationships");
	private static final Set<String> FORBIDDEN = Set.of("extLst", "AlternateContent", "externalReferences",
			"connections", "queryTableParts", "queryTable", "pivotTableParts", "pivotCaches", "oleObjects",
			"oleObject", "controls", "control", "legacyDrawing", "legacyDrawingHF", "drawing", "picture",
			"conditionalFormatting", "dataValidations", "customSheetViews", "webPublishItems", "smartTags",
			"smartTagPr", "cellSmartTags", "scenarios", "dialogsheet", "macrosheet", "threadedComment", "dataModel");
	private static final Set<String> ELEMENTS = Set.of(
			"alignment", "autoFilter", "b", "bgColor", "bookViews", "border", "borders", "bottom",
			"brk", "c", "calcChain", "calcPr", "cellStyle", "cellStyleXfs", "cellStyles", "cellXfs",
			"charset", "col", "colBreaks", "color", "colorFilter", "colors", "cols", "condense",
			"customFilter", "customFilters", "dateGroupItem", "definedName", "definedNames", "diagonal", "dimension", "dxf",
			"dxfs", "dynamicFilter", "evenFooter", "evenHeader", "extend", "f", "family", "fgColor",
			"fileSharing", "fileVersion", "fill", "fills", "filter", "filterColumn", "filters", "firstFooter",
			"firstHeader", "font", "fonts", "gradientFill", "headerFooter", "horizontal", "hyperlink", "hyperlinks",
			"i", "iconFilter", "indexedColors", "is", "left", "mergeCell", "mergeCells", "mruColors",
			"name", "numFmt", "numFmts", "oddFooter", "oddHeader", "outline", "outlinePr", "pageMargins",
			"pageSetUpPr", "pageSetup", "pane", "patternFill", "phoneticPr", "printOptions", "protectedRange", "protectedRanges",
			"protection", "r", "rFont", "rPh", "rPr", "rgbColor", "right", "row",
			"rowBreaks", "scheme", "selection", "shadow", "sheet", "sheetCalcPr", "sheetData", "sheetFormatPr",
			"sheetPr", "sheetProtection", "sheetView", "sheetViews", "sheets", "si", "sortCondition", "sortState",
			"sst", "stop", "strike", "styleSheet", "sz", "t", "tabColor", "tableStyle",
			"tableStyleElement", "tableStyles", "top", "top10", "u", "v", "vertAlign", "vertical",
			"workbook", "workbookPr", "workbookProtection", "workbookView", "worksheet", "xf");
	private final SpreadsheetLimits limits;
	private long sheets;
	private long rows;
	private long cells;
	private long strings;
	private long names;
	private final java.util.Map<String, String> roots = new java.util.HashMap<>();

	public SpreadsheetPreflight(SpreadsheetLimits limits) {
		this.limits = limits;
	}

	@Override
	public void inspect(String part, QName element, Map<QName, String> attributes) throws IOException {
		String root = roots.computeIfAbsent(part, ignored -> element.getNamespaceURI());
		if (!NAMESPACES.contains(element.getNamespaceURI()) || !inScope(root, element.getNamespaceURI())
				|| FORBIDDEN.contains(element.getLocalPart())
				|| (element.getNamespaceURI().equals(MAIN) && !ELEMENTS.contains(element.getLocalPart())))
			throw new SpreadsheetViolation("Unsupported spreadsheet XML namespace or feature");
		for (var attribute : attributes.entrySet()) {
			String name = attribute.getKey().getLocalPart();
			if (!ATTRIBUTES.contains(attribute.getKey().getNamespaceURI()) || name.equals("schemaLocation")
					|| name.equals("noNamespaceSchemaLocation") || name.equals("codeName") || name.equals("action"))
				throw new SpreadsheetViolation("Unsupported spreadsheet XML attribute");
			String namespace = attribute.getKey().getNamespaceURI();
			if (namespace.equals("http://schemas.openxmlformats.org/officeDocument/2006/relationships")
					&& (!name.equals("id") || !element.getNamespaceURI().equals(MAIN)
							|| (!element.getLocalPart().equals("sheet") && !element.getLocalPart().equals("hyperlink"))))
				throw new SpreadsheetViolation("Relationship attribute is unsupported in this context");
			if (namespace.equals("http://www.w3.org/XML/1998/namespace") && !name.equals("space")
					&& !(name.equals("lang") && element.getNamespaceURI().equals("http://purl.org/dc/elements/1.1/")))
				throw new SpreadsheetViolation("Unsupported XML context attribute");
			if (namespace.equals("http://www.w3.org/2001/XMLSchema-instance")
					&& (!name.equals("type") || !element.getNamespaceURI().equals("http://purl.org/dc/terms/")))
				throw new SpreadsheetViolation("Unsupported schema type override");
			if (element.getLocalPart().equals("definedName") && Set.of("function", "vbProcedure", "xlm", "workbookParameter").contains(name)
					&& Set.of("true", "1").contains(attribute.getValue().trim()))
				throw new SpreadsheetViolation("Executable or parameterized defined names are unsupported");
		}
		if (element.getNamespaceURI().equals(MAIN)) {
			if (element.getLocalPart().equals("f")) {
				for (var attribute : attributes.keySet())
					if (!attribute.getNamespaceURI().isEmpty() || !Set.of("t", "ref", "si", "ca").contains(attribute.getLocalPart()))
						throw new SpreadsheetViolation("Unsupported formula representation attribute");
			}
			if (element.getLocalPart().equals("sst")) {
				declared(attributes, "uniqueCount", limits.maxSharedStrings());
				declared(attributes, "count", limits.maxCells());
			}
			switch (element.getLocalPart()) {
			case "sheet" -> { if (++sheets > limits.maxSheets()) throw exhausted(); }
			case "row" -> { if (++rows > limits.maxRows()) throw exhausted(); }
			case "c" -> { if (++cells > limits.maxCells()) throw exhausted(); }
			case "si" -> { if (++strings > limits.maxSharedStrings()) throw exhausted(); }
			case "definedName" -> { if (++names > limits.maxDefinedNames()) throw exhausted(); }
			default -> { }
			}
		}
	}

	private static boolean inScope(String root, String namespace) {
		if (root.equals(namespace)) return true;
		if (root.equals("http://schemas.openxmlformats.org/package/2006/metadata/core-properties"))
			return namespace.equals("http://purl.org/dc/elements/1.1/") || namespace.equals("http://purl.org/dc/terms/");
		if (root.equals("http://schemas.openxmlformats.org/officeDocument/2006/extended-properties")
				|| root.equals("http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"))
			return namespace.equals("http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes");
		return false;
	}

	private static void declared(Map<QName, String> attributes, String name, long maximum) throws SpreadsheetViolation {
		String text = attributes.get(new QName(name));
		if (text != null) {
			try {
				long value = Long.parseLong(text.trim());
				if (value < 0 || value > maximum) throw exhausted();
			} catch (NumberFormatException invalid) {
				throw new SpreadsheetViolation("Invalid declared spreadsheet count");
			}
		}
	}

	private static SpreadsheetViolation exhausted() {
		return new SpreadsheetViolation("Spreadsheet object allowance exhausted");
	}
}
