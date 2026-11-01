# Basic XLSX profile

`filegate-format-spreadsheets` implements `basic-xlsx-v1-poi-6.0.0` as a worker-only
inspection profile. It is narrower than the agreed first-release XLSX target.
It preserves original bytes and never evaluates formulas, refreshes connections,
saves a workbook or starts Excel.

## Current coverage

- Transitional, macro-free XLSX packages under the shared Office ZIP/XML envelope.
- Closed part and relationship roles for workbooks, worksheets, shared strings,
  styles, themes, calculation chains and ordinary document properties. Every part
  must be reachable; root/type mismatches and unlisted worksheet payloads block.
- Closed SpreadsheetML element coverage. Unknown namespaces, compatibility and
  extension markup, active/external features, macro metadata and unsupported
  relationship-bearing attributes block before the workbook model is allocated.
- Pinned schema validation for workbook/worksheet/style/string/theme/calculation
  and extended/custom-property XML. Core properties use a closed flat-text grammar;
  their descriptive values are not authenticated or interpreted as programs.
- Explicit sheet, row, cell, shared-string, defined-name, formula-character/token
  and cell-string budgets, in addition to input/ZIP/XML limits. Hidden sheets and
  all defined names are inspected. Duplicate/out-of-order cells/rows, invalid
  references, name cycles and missing style references block.
- Normal and bounded shared formulas. Every shared master is inspected directly;
  followers must bind to a unique master and a bounded range. Both raw masters and
  expanded formulas are checked, independent of the parser's expansion cache.
- Worksheet hyperlinks require matching allowed relationships or a verified local
  reference/name chain. External URLs follow the explicit Office link policy.

## Formula boundary

Apache POI 6.0.0 supplies the parser. FileGate checks parsed RPN token types,
function allowlists, operand roles, workbook/sheet/name binding and resource bounds.
There is no evaluator. Text containing function names or external-looking references
is permitted as data; it is not scanned with raw-keyword safety rules.

The initial function set includes SUM/AVERAGE/MIN/MAX/COUNT/COUNTA, IF/IFERROR,
AND/OR/NOT, ABS/ROUND/ROUNDUP/ROUNDDOWN/INT/MOD/POWER/SQRT, DATE/YEAR/MONTH/DAY/TODAY/NOW,
LEFT/RIGHT/MID/LEN/TRIM/UPPER/LOWER/CONCATENATE/TEXT/VALUE, SUMIF/SUMIFS/COUNTIF/COUNTIFS,
VLOOKUP/HLOOKUP/INDEX/MATCH, ROW/COLUMN/ROWS/COLUMNS, ISBLANK/ISNUMBER/ISTEXT/ISERROR/ISNA,
NA/TRUE/FALSE/CHOOSE. Unknown token/function representations block.

POI represents some standard functions through extensible-function tokens. Only
IFERROR, SUMIFS and COUNTIFS are accepted there, with internal binding, explicit
argument counts and checked operand roles. This does not permit user-defined or
externally bound functions. Dynamic references, formula-created hyperlinks, network,
macro and add-in functions are outside this profile.

## Still outside this profile

Tables, charts, drawings/images, comments, pivots, conditional formatting,
data-validation formulas, array/data-table formulas, structured table references,
modern dynamic-array functions and extension namespaces need separate coverage and
tests. XLS/XLSB/XLSM, encrypted files, connections, macros, OLE/ActiveX and external
workbooks block. DOCX and PDF remain separate implementation work.

This is not a malware-absence, de-identification, numerical-correctness or arbitrary
viewer-safety guarantee. FileGate does not bound the work a desktop spreadsheet
application may perform when a user opens an original file. Independent review,
fuzzing, representative corpora and measured deployment budgets remain release gates.

## Dependencies

POI and its dependencies are confined to the spreadsheet artifact and isolated
worker classpath. Core remains JDK-only; the Office package artifact remains
independent of POI. Runtime dependencies are included in the aggregate SBOM.
Dependency JARs retain their own license notices and are not shaded into FileGate.
See [Apache's release information](https://poi.apache.org/download.html) and
[the agreed adopter target](adopter-contract.md).
