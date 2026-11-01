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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.formula.FormulaParser;
import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.ss.formula.ptg.AbstractFunctionPtg;
import org.apache.poi.ss.formula.ptg.AddPtg;
import org.apache.poi.ss.formula.ptg.Area3DPxg;
import org.apache.poi.ss.formula.ptg.AreaPtg;
import org.apache.poi.ss.formula.ptg.ArrayPtg;
import org.apache.poi.ss.formula.ptg.AttrPtg;
import org.apache.poi.ss.formula.ptg.BoolPtg;
import org.apache.poi.ss.formula.ptg.ConcatPtg;
import org.apache.poi.ss.formula.ptg.DividePtg;
import org.apache.poi.ss.formula.ptg.EqualPtg;
import org.apache.poi.ss.formula.ptg.ErrPtg;
import org.apache.poi.ss.formula.ptg.GreaterEqualPtg;
import org.apache.poi.ss.formula.ptg.GreaterThanPtg;
import org.apache.poi.ss.formula.ptg.IntPtg;
import org.apache.poi.ss.formula.ptg.IntersectionPtg;
import org.apache.poi.ss.formula.ptg.LessEqualPtg;
import org.apache.poi.ss.formula.ptg.LessThanPtg;
import org.apache.poi.ss.formula.ptg.MemAreaPtg;
import org.apache.poi.ss.formula.ptg.MemFuncPtg;
import org.apache.poi.ss.formula.ptg.MissingArgPtg;
import org.apache.poi.ss.formula.ptg.MultiplyPtg;
import org.apache.poi.ss.formula.ptg.NamePtg;
import org.apache.poi.ss.formula.ptg.NameXPxg;
import org.apache.poi.ss.formula.ptg.NotEqualPtg;
import org.apache.poi.ss.formula.ptg.NumberPtg;
import org.apache.poi.ss.formula.ptg.ParenthesisPtg;
import org.apache.poi.ss.formula.ptg.PercentPtg;
import org.apache.poi.ss.formula.ptg.PowerPtg;
import org.apache.poi.ss.formula.ptg.Ptg;
import org.apache.poi.ss.formula.ptg.RangePtg;
import org.apache.poi.ss.formula.ptg.Ref3DPxg;
import org.apache.poi.ss.formula.ptg.RefPtg;
import org.apache.poi.ss.formula.ptg.StringPtg;
import org.apache.poi.ss.formula.ptg.SubtractPtg;
import org.apache.poi.ss.formula.ptg.UnaryMinusPtg;
import org.apache.poi.ss.formula.ptg.UnaryPlusPtg;
import org.apache.poi.ss.formula.ptg.UnionPtg;
import org.apache.poi.xssf.usermodel.XSSFEvaluationWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Closed parsed-token policy. Never constructs a FormulaEvaluator or evaluates a cell. */
public final class FormulaPolicy {
	private static final Set<String> FUNCTIONS = Set.of("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA",
			"IF", "IFERROR", "AND", "OR", "NOT", "ABS", "ROUND", "ROUNDUP", "ROUNDDOWN", "INT", "MOD",
			"POWER", "SQRT", "DATE", "YEAR", "MONTH", "DAY", "TODAY", "NOW", "LEFT", "RIGHT", "MID", "LEN",
			"TRIM", "UPPER", "LOWER", "CONCATENATE", "TEXT", "VALUE", "SUMIF", "SUMIFS", "COUNTIF", "COUNTIFS",
			"VLOOKUP", "HLOOKUP", "INDEX", "MATCH", "ROW", "COLUMN", "ROWS", "COLUMNS", "ISBLANK", "ISNUMBER",
			"ISTEXT", "ISERROR", "ISNA", "NA", "TRUE", "FALSE", "CHOOSE");
	private static final Set<Class<? extends Ptg>> LOCAL_TOKENS = Set.of(
			IntPtg.class, NumberPtg.class, StringPtg.class, BoolPtg.class, ErrPtg.class,
			MissingArgPtg.class, ArrayPtg.class, RefPtg.class, AreaPtg.class, AddPtg.class,
			SubtractPtg.class, MultiplyPtg.class, DividePtg.class, PowerPtg.class, ConcatPtg.class,
			EqualPtg.class, NotEqualPtg.class, GreaterThanPtg.class, LessThanPtg.class, GreaterEqualPtg.class,
			LessEqualPtg.class, UnaryPlusPtg.class, UnaryMinusPtg.class, PercentPtg.class, ParenthesisPtg.class,
			RangePtg.class, UnionPtg.class, IntersectionPtg.class, MemAreaPtg.class, MemFuncPtg.class);
	private final XSSFWorkbook workbook;
	private final XSSFEvaluationWorkbook context;
	private final SpreadsheetLimits limits;
	private final Set<String> sheets;
	private final int nameCount;

	public FormulaPolicy(XSSFWorkbook workbook, SpreadsheetLimits limits) {
		this.workbook = workbook;
		this.context = XSSFEvaluationWorkbook.create(workbook);
		this.limits = limits;
		this.nameCount = workbook.getNumberOfNames();
		var names = new HashSet<String>();
		for (int index = 0; index < workbook.getNumberOfSheets(); index++)
			names.add(workbook.getSheetName(index).toLowerCase(Locale.ROOT));
		this.sheets = Set.copyOf(names);
	}

	/** Returns referenced defined-name indices so the caller can validate the whole name graph. */
	public Set<Integer> inspect(String formula, int sheetIndex, FormulaType type) throws SpreadsheetViolation {
		return inspect(formula, sheetIndex, type, false);
	}

	public Set<Integer> inspectReference(String formula, int sheetIndex) throws SpreadsheetViolation {
		return inspect(formula, sheetIndex, FormulaType.NAMEDRANGE, true);
	}

	private Set<Integer> inspect(String formula, int sheetIndex, FormulaType type, boolean referenceOnly) throws SpreadsheetViolation {
		if (formula == null || formula.isBlank() || formula.length() > limits.maxFormulaCharacters())
			throw new SpreadsheetViolation("Formula text is missing or exceeds its envelope");
		try {
			Ptg[] tokens = FormulaParser.parse(formula, context, type, sheetIndex);
			if (tokens.length == 0 || tokens.length > limits.maxFormulaTokens())
				throw new SpreadsheetViolation("Formula token allowance exhausted");
			if (referenceOnly && (tokens.length != 1 || !(tokens[0] instanceof RefPtg || tokens[0] instanceof AreaPtg
					|| tokens[0] instanceof Ref3DPxg || tokens[0] instanceof Area3DPxg || tokens[0] instanceof NamePtg)))
				throw new SpreadsheetViolation("Internal hyperlink must name a single workbook reference");
			var names = new HashSet<Integer>();
			var operands = new FormulaOperands();
			for (Ptg token : tokens) {
				if (token instanceof AbstractFunctionPtg function) {
					if (!function.isExternalFunction() && !FUNCTIONS.contains(function.getName().toUpperCase(Locale.ROOT)))
						throw new SpreadsheetViolation("Formula function is outside the declared profile");
				} else if (token instanceof Ref3DPxg reference) {
					internal(reference.getExternalWorkbookNumber(), reference.getSheetName(), reference.getLastSheetName());
				} else if (token instanceof Area3DPxg reference) {
					internal(reference.getExternalWorkbookNumber(), reference.getSheetName(), reference.getLastSheetName());
				} else if (token instanceof NamePtg name) {
					if (name.getIndex() < 0 || name.getIndex() >= nameCount)
						throw new SpreadsheetViolation("Formula refers to an unknown defined name");
					names.add(name.getIndex());
				} else if (token instanceof AttrPtg attribute) {
					if (!attribute.isSum() && !attribute.isOptimizedIf() && !attribute.isOptimizedChoose()
							&& !attribute.isSkip() && !attribute.isSpace())
						throw new SpreadsheetViolation("Unsupported formula control token");
				} else if (!(token instanceof NameXPxg) && !LOCAL_TOKENS.contains(token.getClass())) {
					throw new SpreadsheetViolation("Unsupported formula token or external reference");
				}
				operands.accept(token);
			}
			operands.finish();
			if (workbook.getNumberOfNames() != nameCount || workbook.getNumberOfSheets() != sheets.size())
				throw new SpreadsheetViolation("Formula parsing changed workbook metadata");
			return Set.copyOf(names);
		} catch (FormulaParseException | IllegalArgumentException | IllegalStateException malformed) {
			throw new SpreadsheetViolation("Formula is malformed or outside the supported grammar");
		}
	}

	private void internal(int externalWorkbook, String firstSheet, String lastSheet) throws SpreadsheetViolation {
		if (externalWorkbook >= 0 || !sheets.contains(firstSheet.toLowerCase(Locale.ROOT))
				|| (lastSheet != null && !sheets.contains(lastSheet.toLowerCase(Locale.ROOT))))
			throw new SpreadsheetViolation("Formula refers outside the declared workbook");
	}
}
