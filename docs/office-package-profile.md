# Office package boundary

The `filegate-format-office` artifact supplies a worker-only shared ZIP/XML
inventory reader for future DOCX/XLSX inspectors. **Inventory is not document
acceptance.** The API returns no core `CheckEvidence` or approved-content handle;
no Office inspector is registered in the gate yet.

## Implemented envelope

- Classic, single-disk ZIP with stored or deflated entries. ZIP64, encryption,
  archive/entry comments, self-extracting prefixes and trailing data are unsupported.
- Central and local headers must describe the same ordered, non-overlapping,
  contiguous entries, including names, flags, methods, sizes, CRC and descriptors.
  Alternate Unicode path extras, ZIP64/AES extras and filesystem links are rejected.
- Bounded ASCII part paths with no absolute paths, traversal, escaped aliases,
  backslashes, case-fold collisions or empty segments. No files are extracted.
- Declared input, entry count and expansion checks precede decompression. Actual
  expanded bytes are counted independently per part and across the package.
  Deflate termination, compressed-byte counts, uncompressed sizes and CRC must agree.
- Every `.xml` and `.rels` part is parsed as XML, using the JDK default StAX factory.
  DTDs, entity references, external resources and processing instructions are
  forbidden. XML events and character budgets apply across parts; element depth,
  attributes and namespace declarations are bounded. Namespace-aware root names
  are recorded without resolving links or executing content.

The reader requires an explicit `OfficePackageLimits`; there are no production
budget defaults. It must run inside a bounded worker against privately captured
bytes. Bounds in this parser do not replace OS CPU/memory limits or task deadlines.

## Still required for DOCX/XLSX evidence

Inventory does not establish an OOXML package type, part content types, relationship
validity, schema conformance, required document/workbook structure, safe formulas,
embedded-media coverage or absence of active features. Those require explicit
format-specific allowlists, semantic checks and an isolated worker integration.
For example, an arbitrary well-formed XML ZIP may produce inventory but cannot
satisfy a mandatory DOCX/XLSX content check.

The accepted product target is in [the adopter contract](adopter-contract.md).
Spreadsheet parts are defined by [Microsoft's SpreadsheetML structure reference](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document).
OOXML package and compatibility semantics come from [ECMA-376](https://ecma-international.org/publications-and-standards/standards/ecma-376/).
Unknown extension/alternate-content representations must not bypass future checks.
