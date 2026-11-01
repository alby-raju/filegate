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
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.openxmlformats.schemas.drawingml.x2006.main.ThemeDocument;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CalcChainDocument;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.SstDocument;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.StyleSheetDocument;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.WorkbookDocument;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.WorksheetDocument;

import dev.filegate.formats.office.OfficePackageManifest;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

public final class SpreadsheetSchemas {
	private SpreadsheetSchemas() { }

	public static void verify(Path captured, OfficePackageManifest manifest) throws IOException {
		var options = new XmlOptions().setDisallowDocTypeDeclaration(true).setLoadExternalDTD(false)
				.setLoadDTDGrammar(false).setEntityExpansionLimit(0);
		try (var zip = new ZipFile(captured.toFile())) {
			for (var part : manifest.contentTypes().entrySet()) {
				try (var input = zip.getInputStream(zip.getEntry(part.getKey()))) {
					if (part.getValue().equals("application/vnd.openxmlformats-package.core-properties+xml")) {
						CoreProperties.verify(input);
						continue;
					}
					XmlObject document = switch (part.getValue()) {
					case SpreadsheetPackageGraph.BOOK -> WorkbookDocument.Factory.parse(input, options);
					case SpreadsheetPackageGraph.SHEET -> WorksheetDocument.Factory.parse(input, options);
					case SpreadsheetPackageGraph.STYLES -> StyleSheetDocument.Factory.parse(input, options);
					case SpreadsheetPackageGraph.STRINGS -> SstDocument.Factory.parse(input, options);
					case SpreadsheetPackageGraph.THEME -> ThemeDocument.Factory.parse(input, options);
					case SpreadsheetPackageGraph.CHAIN -> CalcChainDocument.Factory.parse(input, options);
					case "application/vnd.openxmlformats-officedocument.extended-properties+xml" ->
						org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.PropertiesDocument.Factory.parse(input, options);
					case "application/vnd.openxmlformats-officedocument.custom-properties+xml" ->
						org.openxmlformats.schemas.officeDocument.x2006.customProperties.PropertiesDocument.Factory.parse(input, options);
					default -> null; // Relationship declarations were validated by the manifest reader.
					};
					if (document != null && !document.validate())
						throw new SpreadsheetViolation("Spreadsheet part violates its declared schema");
				}
			}
		} catch (XmlException malformed) {
			throw new SpreadsheetViolation("Spreadsheet schema parsing failed");
		}
	}
}
