# First adopter workflow

The user selected local POSIX storage, inspection only, with AV optional.
This narrows the deployment target without removing planned PNG/JPEG, PDF/DOCX/XLSX or
mixed-attachment coverage. It does not establish an external adopter or production
approval.

## Decided

- One host application owns authentication, authorization and upload admission.
- Captured originals are inspected; no preparation or derivative is produced.
- Local POSIX private staging and pending-object publication are the first storage
  target. Approved bytes are verified after persistence before atomic no-replace
  publication. Published objects survive gate shutdown.
- PNG/JPEG inspection uses the explicit isolated Linux image check. Unsupported
  formats block until their mandatory inspectors are implemented and installed.
- AV is optional. Installing a scanner must never activate it implicitly. A policy
  that explicitly requires an AV capability must fail compilation if it is absent;
  required-scan outages or uncertainty must block approval. No malware-absence claim
  is made for policies that omit AV.

## Agreed initial document target

- Formats: PNG, JPEG, PDF, DOCX and XLSX, including mixed-attachment uploads.
- Use: private storage and download first. Inline preview requires a later policy.
- Upload ceiling: configurable 25 MiB as the initial product target, subject to
  representative-file and resource-envelope validation.
- PDF: ordinary text, scanned pages, images, bookmarks and standard hyperlinks.
  Scripts, embedded attachments, encrypted documents and interactive forms are
  outside the initial profile.
- DOCX: ordinary text, tables, images, headers/footers, comments and tracked changes.
  Reject macros, embedded objects, active content and automatic external fetching.
- XLSX: ordinary cells, formatting, multiple sheets, tables, charts, formulas,
  internal references, hidden sheets, comments and named ranges. Preserve approved
  bytes without calculating formulas or refreshing connections. External workbook
  references, data connections, macros, ActiveX, executable/OLE objects and encrypted
  workbooks are outside the initial profile. XLS/XLSB/XLSM and CSV are deferred.
- Standard clickable hyperlinks require an explicit scheme/link policy. They do
  not authorize automatic fetching of linked resources. Formula/link distinctions
  must be checked structurally rather than by searching raw XML text.
- Per-format expansion, XML, object, image, sheet/cell/string/formula and task limits
  are separate from the upload ceiling. Supported representations must be documented
  and tested before the corresponding inspector can issue passing evidence.

These are agreed implementation targets, not claims of implemented coverage.

## Still required before freezing M3.1

- Validate concrete host response rules for private storage and download.
- Agree deployment limits for input size, staging, pixels, decoded data, worker
  concurrency, memory, CPU, scratch and timeouts. Test values are not defaults.
- Validate the detailed PDF/DOCX/XLSX representations and acceptable rejection rate.
- Obtain a representative adopter corpus, including malformed and unsupported files.
- Validate the actual filesystem, crash durability and ambiguous-commit recovery.

The local example is not a complete web server or an authorization system. The
Linux integration test composes isolated image inspection with verified local
publication and asserts that blocked content creates no public object. Real host
integration and qualification remain M3/M5 work.
