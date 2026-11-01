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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dev.filegate.formats.office.OfficeManifestReader;
import dev.filegate.formats.spreadsheets.internal.SpreadsheetPackageGraph;
import dev.filegate.formats.spreadsheets.internal.SpreadsheetPreflight;
import dev.filegate.formats.spreadsheets.internal.SpreadsheetSchemas;
import dev.filegate.formats.spreadsheets.internal.WorkbookSemantics;

/** Basic XLSX worker-only profile. Original bytes are never saved, refreshed or calculated. */
public final class RestrictedSpreadsheetInspector {
	public static final String PROFILE = "basic-xlsx-v1-poi-6.0.0";
	private final SpreadsheetProfile profile;

	public RestrictedSpreadsheetInspector(SpreadsheetProfile profile) {
		this.profile = Objects.requireNonNull(profile, "profile");
	}

	public SpreadsheetReport inspect(Path captured) throws IOException {
		var manifest = new OfficeManifestReader(profile.packageLimits(), profile.linkPolicy())
				.read(captured, new SpreadsheetPreflight(profile.spreadsheetLimits()));
		SpreadsheetPackageGraph.verify(manifest);
		SpreadsheetSchemas.verify(captured, manifest);
		try (var pkg = OPCPackage.open(captured.toFile(), PackageAccess.READ); var workbook = new XSSFWorkbook(pkg)) {
			return WorkbookSemantics.verify(workbook, manifest, profile.spreadsheetLimits());
		} catch (InvalidFormatException | IllegalArgumentException | IllegalStateException malformed) {
			throw new SpreadsheetViolation("Spreadsheet model is invalid or unsupported");
		} catch (RuntimeException uncertain) {
			throw new IOException("Spreadsheet parser could not complete", uncertain);
		}
	}
}
