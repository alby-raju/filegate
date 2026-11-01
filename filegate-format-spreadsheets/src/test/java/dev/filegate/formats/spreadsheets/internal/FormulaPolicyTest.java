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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

class FormulaPolicyTest {
	private SpreadsheetLimits limits() {
		return new SpreadsheetLimits(8, 1000, 10000, 1000, 100, 8192, 4096, 32767);
	}

	private XSSFWorkbook workbook() {
		var workbook = new XSSFWorkbook();
		workbook.createSheet("Sheet1");
		workbook.createSheet("Sales 2026");
		var rate = workbook.createName();
		rate.setNameName("Rate");
		rate.setRefersToFormula("Sheet1!$A$1");
		return workbook;
	}

	@Test
	void commonCalculationsInternalReferencesAndDefinedNamesArePreservedWithoutEvaluation() throws Exception {
		try (var workbook = workbook()) {
			var policy = new FormulaPolicy(workbook, limits());
			for (String formula : new String[] { "SUM(A1:A5)", "IF(A1>0,ROUND(A1*2,2),0)",
					"'Sales 2026'!$A$1+Sheet1!B2", "AVERAGE(A1:A5)", "COUNTIF(A1:A5,1)",
					"SUMIF(A1:A5,1,B1:B5)", "IFERROR(1/0,0)", "INDEX(A1:B5,2,1)",
					"DATE(2026,1,1)", "\"ordinary text\"&LEFT(\"abc\",2)", "SUM({1,2,3})" })
				assertTrue(policy.inspect(formula, 0, FormulaType.CELL).isEmpty(), formula);
			assertEquals(java.util.Set.of(0), policy.inspect("SUM(A1:A5)*Rate", 0, FormulaType.CELL));
			assertEquals(1, workbook.getNumberOfNames());
		}
	}

	@Test
	void dynamicReferencesNetworkFunctionsAndUserDefinedFunctionsReject() throws Exception {
		try (var workbook = workbook()) {
			var policy = new FormulaPolicy(workbook, limits());
			for (String formula : new String[] { "WEBSERVICE(\"https://example.invalid\")", "INDIRECT(\"A1\")",
					"HYPERLINK(\"https://example.invalid\",\"click\")", "OFFSET(A1,1,0)",
					"RTD(\"server\",\"\",\"topic\")", "CALL(\"library\",\"entry\",\"J\")", "CustomFunction(A1)" })
				assertThrows(SpreadsheetViolation.class, () -> policy.inspect(formula, 0, FormulaType.CELL), formula);
		}
	}

	@Test
	void externalWorkbooksUnknownSheetsAndUndefinedNamesReject() throws Exception {
		try (var workbook = workbook()) {
			var policy = new FormulaPolicy(workbook, limits());
			for (String formula : new String[] { "'[book.xlsx]Sheet1'!A1", "MissingSheet!A1", "UnknownName+1",
					"'https://example.invalid/[book.xlsx]Sheet1'!A1", "cmd|' /C calc'!A1" })
				assertThrows(SpreadsheetViolation.class, () -> policy.inspect(formula, 0, FormulaType.CELL), formula);
		}
	}

	@Test
	void stringsContainingExternalLookingTextRemainPlainData() throws Exception {
		try (var workbook = workbook()) {
			var policy = new FormulaPolicy(workbook, limits());
			policy.inspect("IF(A1=1,\"[book.xlsx]cmd|dde!A1\",\"WEBSERVICE\")", 0, FormulaType.CELL);
		}
	}

	@Test
	void formulaCharactersAndParsedTokensHaveIndependentLimits() throws Exception {
		try (var workbook = workbook()) {
			var shortText = new FormulaPolicy(workbook, new SpreadsheetLimits(8, 1000, 10000, 1000, 100, 2, 4096, 32767));
			assertThrows(SpreadsheetViolation.class, () -> shortText.inspect("1+2", 0, FormulaType.CELL));
			var oneToken = new FormulaPolicy(workbook, new SpreadsheetLimits(8, 1000, 10000, 1000, 100, 8192, 1, 32767));
			assertThrows(SpreadsheetViolation.class, () -> oneToken.inspect("1+2", 0, FormulaType.CELL));
		}
	}

	@Test
	void metadataIsFrozenBeforeFormulaParsing() throws Exception {
		try (var workbook = workbook()) {
			var policy = new FormulaPolicy(workbook, limits());
			var later = workbook.createName();
			later.setNameName("Later");
			later.setRefersToFormula("Sheet1!A1");
			assertThrows(SpreadsheetViolation.class, () -> policy.inspect("Later", 0, FormulaType.CELL));
		}
	}

	@Test
	void malformedAndUnsupportedGrammarCannotPass() throws Exception {
		try (var workbook = workbook()) {
			var policy = new FormulaPolicy(workbook, limits());
			for (String formula : new String[] { "", " ", "1+", "SUM(", "A0", "1;2", "Table1[Sales]" })
				assertThrows(SpreadsheetViolation.class, () -> policy.inspect(formula, 0, FormulaType.CELL), formula);
		}
	}
}
