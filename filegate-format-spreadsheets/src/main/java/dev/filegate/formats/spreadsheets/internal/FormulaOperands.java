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
import java.util.Deque;
import java.util.Locale;

import org.apache.poi.ss.formula.ptg.AbstractFunctionPtg;
import org.apache.poi.ss.formula.ptg.AttrPtg;
import org.apache.poi.ss.formula.ptg.MemAreaPtg;
import org.apache.poi.ss.formula.ptg.MemFuncPtg;
import org.apache.poi.ss.formula.ptg.NameXPxg;
import org.apache.poi.ss.formula.ptg.OperationPtg;
import org.apache.poi.ss.formula.ptg.ParenthesisPtg;
import org.apache.poi.ss.formula.ptg.Ptg;

import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Checks RPN operand roles; values are never calculated or resolved. */
final class FormulaOperands {
	private sealed interface Operand permits Value, Intrinsic { }
	private enum Value implements Operand { PRESENT }
	private record Intrinsic(String name) implements Operand { }
	private final Deque<Operand> stack = new ArrayDeque<>();

	void accept(Ptg token) throws SpreadsheetViolation {
		if (token instanceof NameXPxg name) {
			String function = name.getNameName().toUpperCase(Locale.ROOT);
			if (function.startsWith("_XLFN."))
				function = function.substring(6);
			if (name.getExternalWorkbookNumber() >= 0 || name.getSheetName() != null
					|| (!function.equals("IFERROR") && !function.equals("SUMIFS") && !function.equals("COUNTIFS")))
				throw invalid();
			stack.push(new Intrinsic(function));
		} else if (token instanceof AbstractFunctionPtg function) {
			int count = function.getNumberOfOperands();
			if (function.isExternalFunction()) {
				if (count < 1)
					throw invalid();
				values(count - 1);
				if (!(stack.poll() instanceof Intrinsic intrinsic) || !arity(intrinsic.name(), count - 1))
					throw invalid();
			} else {
				values(count);
			}
			stack.push(Value.PRESENT);
		} else if (token instanceof AttrPtg attribute) {
			if (attribute.isSum()) {
				values(1);
				stack.push(Value.PRESENT);
			}
		} else if (token instanceof OperationPtg operation) {
			values(operation.getNumberOfOperands());
			stack.push(Value.PRESENT);
		} else if (token instanceof ParenthesisPtg) {
			if (stack.peek() != Value.PRESENT)
				throw invalid();
		} else if (!(token instanceof MemAreaPtg) && !(token instanceof MemFuncPtg)) {
			stack.push(Value.PRESENT);
		}
	}

	void finish() throws SpreadsheetViolation {
		if (stack.size() != 1 || stack.peek() != Value.PRESENT)
			throw invalid();
	}

	private void values(int count) throws SpreadsheetViolation {
		for (int index = 0; index < count; index++) {
			if (stack.poll() != Value.PRESENT)
				throw invalid();
		}
	}

	private static boolean arity(String name, int count) {
		return switch (name) {
		case "IFERROR" -> count == 2;
		case "SUMIFS" -> count >= 3 && count <= 255 && count % 2 == 1;
		case "COUNTIFS" -> count >= 2 && count <= 254 && count % 2 == 0;
		default -> false;
		};
	}

	private static SpreadsheetViolation invalid() {
		return new SpreadsheetViolation("Unsupported formula operand roles or extensible function");
	}
}
