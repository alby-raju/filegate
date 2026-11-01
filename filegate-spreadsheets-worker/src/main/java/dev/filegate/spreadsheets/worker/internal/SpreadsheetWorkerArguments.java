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
package dev.filegate.spreadsheets.worker.internal;

import java.util.List;

import dev.filegate.formats.office.OfficeLinkPolicy;
import dev.filegate.formats.office.OfficePackageLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetProfile;
import dev.filegate.worker.linux.SandboxLimits;

/** Fixed positional deployment contract; no file claims or user-supplied commands enter it. */
public final class SpreadsheetWorkerArguments {
	public record Configuration(SandboxLimits sandbox, SpreadsheetProfile profile) { }

	private SpreadsheetWorkerArguments() { }

	public static List<String> encode(SandboxLimits sandbox, SpreadsheetProfile profile) {
		var zip = profile.packageLimits();
		var sheet = profile.spreadsheetLimits();
		return List.of(Long.toString(sandbox.memoryBytes()), Long.toString(sandbox.scratchBytes()),
				Integer.toString(sandbox.pids()), Integer.toString(sandbox.cpuMillisPerSecond()),
				Long.toString(zip.maxInputBytes()), Long.toString(zip.maxExpandedBytes()), Long.toString(zip.maxPartBytes()),
				Integer.toString(zip.maxEntries()), Integer.toString(zip.maxXmlDepth()), Long.toString(zip.maxXmlEvents()),
				Long.toString(zip.maxXmlCharacters()), Integer.toString(sheet.maxSheets()), Long.toString(sheet.maxRows()),
				Long.toString(sheet.maxCells()), Integer.toString(sheet.maxSharedStrings()), Integer.toString(sheet.maxDefinedNames()),
				Integer.toString(sheet.maxFormulaCharacters()), Integer.toString(sheet.maxFormulaTokens()),
				Integer.toString(sheet.maxCellCharacters()), profile.linkPolicy().name());
	}

	public static Configuration decode(String[] args) {
		if (args.length != 20) throw new IllegalArgumentException("Invalid spreadsheet worker configuration");
		var sandbox = SandboxLimits.parse(args, 0);
		var zip = new OfficePackageLimits(Long.parseLong(args[4]), Long.parseLong(args[5]), Long.parseLong(args[6]),
				Integer.parseInt(args[7]), Integer.parseInt(args[8]), Long.parseLong(args[9]), Long.parseLong(args[10]));
		var sheet = new SpreadsheetLimits(Integer.parseInt(args[11]), Long.parseLong(args[12]), Long.parseLong(args[13]),
				Integer.parseInt(args[14]), Integer.parseInt(args[15]), Integer.parseInt(args[16]), Integer.parseInt(args[17]),
				Integer.parseInt(args[18]));
		if (zip.maxInputBytes() > sandbox.scratchBytes() / 2)
			throw new IllegalArgumentException("Spreadsheet input exceeds scratch envelope");
		return new Configuration(sandbox, new SpreadsheetProfile(zip, sheet, OfficeLinkPolicy.valueOf(args[19])));
	}
}
