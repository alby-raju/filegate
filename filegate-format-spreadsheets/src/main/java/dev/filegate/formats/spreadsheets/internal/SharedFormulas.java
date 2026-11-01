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
import java.util.Map;

import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell;

import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Keeps shared master identity separate from the parser model's cached expansion. */
final class SharedFormulas {
	private final Map<Long, CellRangeAddress> masters = new HashMap<>();
	private final FormulaPolicy policy;
	private final SpreadsheetLimits limits;
	private final int sheetIndex;

	SharedFormulas(FormulaPolicy policy, SpreadsheetLimits limits, int sheetIndex) {
		this.policy = policy;
		this.limits = limits;
		this.sheetIndex = sheetIndex;
	}

	void inspect(CTCell cell, CellReference reference) throws SpreadsheetViolation {
		var formula = cell.getF();
		String text = formula.getStringValue();
		String type = formula.getT().toString();
		if (type.equals("normal")) {
			if (formula.isSetSi() || formula.isSetRef())
				throw new SpreadsheetViolation("Unexpected normal-formula sharing metadata");
			policy.inspect(text, sheetIndex, FormulaType.CELL);
		} else if (type.equals("shared")) {
			if (!formula.isSetSi())
				throw new SpreadsheetViolation("Shared formula identifier is missing");
			CellRangeAddress range;
			if (text != null && !text.isBlank()) {
				if (!formula.isSetRef())
					throw new SpreadsheetViolation("Shared formula master range is missing");
				range = CellRangeAddress.valueOf(formula.getRef());
				long cells = (long) (range.getLastRow() - range.getFirstRow() + 1)
						* (range.getLastColumn() - range.getFirstColumn() + 1);
				if (range.getFirstRow() != reference.getRow() || range.getFirstColumn() != reference.getCol()
						|| range.getLastRow() >= 1048576 || range.getLastColumn() >= 16384 || cells > limits.maxCells()
						|| masters.putIfAbsent(formula.getSi(), range) != null)
					throw new SpreadsheetViolation("Duplicate or unsupported shared formula master");
				// Audit the actual master even if the object model would expand a different cached master.
				policy.inspect(text, sheetIndex, FormulaType.CELL);
			} else {
				range = masters.get(formula.getSi());
				if (range == null || formula.isSetRef() || !range.isInRange(reference.getRow(), reference.getCol()))
					throw new SpreadsheetViolation("Shared formula follower has no matching bounded master");
			}
		} else {
			throw new SpreadsheetViolation("Array and data-table formula representations are not supported yet");
		}
	}
}
