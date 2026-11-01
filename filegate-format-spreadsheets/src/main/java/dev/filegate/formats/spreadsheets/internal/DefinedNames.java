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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Every definition is checked, including hidden and otherwise unused names. */
public final class DefinedNames {
	private final FormulaPolicy policy;
	private final List<XSSFName> names;

	public DefinedNames(XSSFWorkbook workbook, FormulaPolicy policy) throws SpreadsheetViolation {
		this.policy = policy;
		this.names = List.copyOf(workbook.getAllNames());
		var scopes = new HashSet<String>();
		var graph = new ArrayList<Set<Integer>>(names.size());
		for (var name : names) {
			if (name.isFunctionName() || name.getFunction() || name.getSheetIndex() < -1
					|| name.getSheetIndex() >= workbook.getNumberOfSheets()
					|| !scopes.add(name.getNameName().toLowerCase(Locale.ROOT) + ":" + name.getSheetIndex()))
				throw new SpreadsheetViolation("Invalid, duplicate or executable defined name");
			graph.add(policy.inspect(name.getRefersToFormula(), name.getSheetIndex(), FormulaType.NAMEDRANGE));
		}
		acyclic(graph);
	}

	public void reference(String expression, int sheetIndex) throws SpreadsheetViolation {
		var pending = new ArrayDeque<Integer>(policy.inspectReference(expression, sheetIndex));
		var seen = new HashSet<Integer>();
		while (!pending.isEmpty()) {
			int index = pending.removeFirst();
			if (seen.add(index)) {
				var name = names.get(index);
				pending.addAll(policy.inspectReference(name.getRefersToFormula(), name.getSheetIndex()));
			}
		}
	}

	private static void acyclic(List<Set<Integer>> graph) throws SpreadsheetViolation {
		int[] inbound = new int[graph.size()];
		for (var edges : graph)
			for (int target : edges) inbound[target]++;
		var pending = new ArrayDeque<Integer>();
		for (int index = 0; index < inbound.length; index++)
			if (inbound[index] == 0) pending.add(index);
		int visited = 0;
		while (!pending.isEmpty()) {
			int index = pending.removeFirst();
			visited++;
			for (int target : graph.get(index))
				if (--inbound[target] == 0) pending.add(target);
		}
		if (visited != graph.size())
			throw new SpreadsheetViolation("Cyclic defined-name references are unsupported");
	}
}
