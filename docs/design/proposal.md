<!-- Transcribed from the unchanged source DOCX. Body revision: 0.5; source filename: v0.4. Layout is not preserved. -->

# FileGate Design Proposal and Architecture

Policy based attachment inspection and verified release with optional preparation

Revision 0.5 • 1 November 2026 • Proposed architecture, not an implemented or audited product

### Executive decision

Build FileGate as a small Java integration that inspects attachments consistently and releases only the approved bytes. Keep preparation independently optional. The first public release must complete a real adopter’s attachment workflow; PNG/JPEG is an engineering milestone, while PDF and DOCX enter the launch scope when the selected workflow requires them.

The core coordinates bounded capture, immutable policies, inspection evidence and verified transfer. Complex parsers run in isolated workers. Preparation adds explicit transformations with separate input eligibility and output acceptance policies; transformed output is sealed and independently rechecked before it can be released.

### Product value

The initial adopter is a Java team with multiple upload paths and inconsistent validation or failure handling. FileGate should reduce security-specific application code, make required checks explicit, and provide predictable resource and publication behavior. A team needing only an extension check is unlikely to accept the additional integration and deployment cost.

### Changes from revision 0 4

| Concern | Resolved design direction |
| --- | --- |
| Preparation expands the core | Separate optional preparation artifact and explicit capability registration; no transformation dependencies for inspect-only users. |
| Input rejection conflicts with cleanup | Use a dedicated eligibility policy and a distinct derivative release policy. |
| Transformer can still mutate output | Terminate task writers, capture output into host-owned staging, then verify in a fresh task. |
| Output allocation can deadlock | Reserve the whole bounded preparation envelope before capture; no waiting for extra quota while holding input. |
| Intended use sounds like a guarantee | Bind use to handles and receipts; report host obligations separately from completed checks. |
| Publication example omits abort | Require a private pending destination that aborts unless finalization and commit succeed. |

### Claims and evidence

APPROVED means that exact bytes passed a named policy and coverage profile. It does not establish universal malware absence, safe execution, legal de-identification, or safety in every viewer. Adoption, latency and footprint remain unproven. Differentiation must be demonstrated through coverage, integration effort, reliability and measured performance.

## 1 Scope and acceptance contracts

Propose Java 21 as the minimum baseline and a framework-independent blocking API. Create one long-lived gate per configured resource domain. Results are scoped, explicitly closed and not shared across callers. The core requires no database, web server, cloud service or document editing framework.

### Operations and dependency boundaries

| Operation | Contract | Availability |
| --- | --- | --- |
| Inspect | Capture input and determine whether those bytes satisfy an admission policy. | Core plus explicitly installed inspectors. |
| Prepare | Accept eligible input, apply named operations, seal a new derivative and evaluate its release policy. | Separate preparation artifact and transformation workers. |
| Release | Copy approved bytes to a private destination with length and digest verification; the host commits visibility. | Core; persistence and authorization remain host-owned. |

### Three public outcomes

APPROVED exposes an approved-content handle only after all mandatory checks pass. REJECTED means a definite policy violation or expressly prohibited unsupported condition was established. INCOMPLETE means required evidence could not be established because of uncertainty, capacity, failure, cancellation or timeout. Neither blocking outcome exposes approved content.

For one artifact evaluated against one policy, precedence is REJECTED before INCOMPLETE before APPROVED. A known size violation rejects; a busy worker pool is incomplete. Unknown parser exceptions are incomplete, not proof of malicious content. Early denial records remaining checks as NOT_RUN. Missing required evidence never permits approval.

### Policy compilation

Compile immutable policies at startup, resolving explicit capabilities, versions, required check identifiers, intended use and resource limits. Reject unknown keys, unsupported guarantees, contradictory rules and incompatible budgets. Canonical policy fingerprints bind every result to the resolved contract. A reload replaces the snapshot; existing work retains its original snapshot.

Construction fails when a mandatory implementation is absent. Runtime worker or scanner outages produce blocking results without weaker fallback. Adding a dependency never silently adds a check, broadens coverage or changes a policy.

### Initial exclusions

Exclude semantic PII/PHI discovery, targeted redaction, OCR, arbitrary conversion, general archive extraction, universal HTML/SVG sanitization, authenticity evaluation, automatic upload interception and a new malware engine from the initial committed scope. Deterministic preparation is added only when the selected pilot workflow requires it. No cache, generic workflow graph or per-check thread pool is needed in the baseline.

## 2 Capture preflight and isolated inspection

### Inspect only execution

Reserve capacity, capture the input, resolve format candidates, run required inspection, aggregate evidence and expose approved content. Preflight can reject during capture; approval waits for complete capture and all mandatory checks. Hash input while writing it to private staging with a fixed-size buffer. Count actual bytes, never only declared length.

Preflight checks the source envelope, permitted claims and bounded signatures. It does not establish complete structure or absence of polyglots. Client MIME and filenames remain untrusted claims. Avoid arbitrary-byte XSS searches. Safe browser rendering depends on downstream context and host behavior. [1,2]

### Protected captured bytes

Generate staging names under a private root; never derive filesystem paths from user metadata. Copy Path input into owned staging by default. Close the writer before inspection, restrict worker access to read-only input, and never expose the stage path through the public API. The host and operating system are trusted; a malicious process with equivalent host privileges is outside the guarantee.

### Execution profiles

EMBEDDED supports bounded core checks and explicitly accepted in-process inspectors with cooperative deadlines. ISOLATED is required for the proposed hostile-file format profiles. A separate JVM alone is not a sandbox: the supported deployment must enforce filesystem, network, memory, CPU and process restrictions. Heap ceilings do not bound all resident memory. Tika documents this parser trust boundary. [2]

Initially validate a Linux worker deployment with non-root identity, read-only code, no credentials, no outbound network, task-specific input access and bounded private scratch. Do not silently substitute embedded execution on an unsupported platform. Isolation limits damage; a compromised parser can still give incorrect findings.

### Worker protocol and scheduling

Use a fixed pool and one active task per worker. Cap frame sizes, findings, diagnostics and stderr; do not deserialize arbitrary Java objects. Bind each response to request ID, artifact digest, policy fingerprint, inspector build and expected check set. Reject missing, duplicated, unknown or late results. Crashes and timeouts invalidate the task, trigger controlled recycling and use restart backoff.

Run checks sequentially within a file, in cost order where dependencies permit. Concurrent files share bounded capacity. Expensive optional AV normally follows cheap rejection checks and scans the exact selected artifact. A required scanner’s errors, uncertain coverage or exceeded scan limits cannot count as a clean result. [6]

### Deadline boundaries

Worker deadlines terminate the worker task. Arbitrary InputStream or OutputStream calls may block despite interruption, so the host must provide transport and destination timeouts. Do not claim hard end-to-end timeout guarantees for generic streams. Report phase timings separately.

## 3 Optional preparation with separate policies

The preparation module depends on core; core never depends on preparation. Inspect-only installation loads no transformer, encoder or transformation configuration. Use a separate FilePreparer facade sharing the core’s resource and artifact services. Capability registration is explicit and validated before traffic.

### Eligibility is different from release approval

InputEligibilityPolicy establishes that a file can enter a specific transformation under a bounded supported profile. It declares mandatory input checks, unconditional prohibitions and expressly transformable findings. OutputReleasePolicy establishes what the new derivative must satisfy before release. These policies have separate fingerprints and reports.

| Example input finding | Eligibility treatment | Output requirement |
| --- | --- | --- |
| Supported author metadata | May proceed only when removal is explicitly selected. | Named author fields are absent under the declared coverage. |
| Encryption without supported access | Reject eligibility. | No derivative is released. |
| Missing required input scan or parser evidence | INCOMPLETE; do not transform. | No original-file fallback. |
| Unsupported embedded object | Reject under the initial restricted profile. | Removal is not assumed merely because a rewrite occurred. |

An internal ELIGIBLE status grants permission to transform, not an approved-content handle. Do not apply the final release policy to the input and then override its rejection. Evaluate verdict precedence independently at each phase. Original admission failure, transformability and derivative approval are distinct facts.

### A narrow initial capability

Start, only if demanded, with deterministic removal of enumerated fields or structures in a named format profile. Specify each operation’s preconditions, exact target locations, unsupported constructs, expected fidelity effects and verification procedure. A generic remove-sensitive-information operation is outside the contract.

Restricted reconstruction is a separately gated capability with its own renderer/encoder and fidelity corpus. Targeted redaction remains outside the initial committed scope. Office hidden content and PDF sanitization have format-specific limitations; visible content, embedded images, revisions and caches cannot be treated as interchangeable representations. [4,5]

### Failure and fidelity

Transformation produces new bytes and a new digest. Failure, missing verification or output policy denial never returns the original as an approved substitute. Record operation identifiers and bounded findings without logging removed secrets. Reject signed inputs by default; allow invalidation only through an explicit policy choice and report that original signatures do not authenticate the derivative.

Security acceptance and document fidelity are separate checks. When preservation of formulas, layout or content is required, define and execute the supported fidelity check. If that property cannot be established, the preparation is incomplete or rejected according to its explicit contract.

## 4 Seal and verify the derivative

### Output handoff is a new trust boundary

The transformer writes only to its task-private output area. It must not write directly to host-approved staging or publish a destination. Preallocate an output/scratch allowance and enforce it while writing, not after a potentially unlimited file has been produced.

Finish the transformation task and terminate its process tree, confirming that all task writers have exited. A returned success message or closing one stream is insufficient.

Validate the output as a regular permitted file in the task area. Reject links, unexpected outputs and inconsistent metadata; never follow a worker-supplied arbitrary path.

Copy output under the reserved byte limit into a new host-owned stage, calculating digest and length. Close its writer and remove transformation write access. Only this captured derivative is eligible for verification.

Run output checks in a fresh verification task with read-only access to the sealed derivative. Bind findings to its digest and output policy. The transformer’s removal report is advisory evidence, never a substitute for verification.

Aggregate the derivative result and expose its content handle only if every mandatory release check passes. Dispose task outputs and input stages according to owned leases.

For preparation v1, use a disposable transformation process for each task. A supervisor may be long-lived, but transformation code must not retain writable state across the handoff. This costs startup time; measure it explicitly. Reuse is a later optimization requiring equivalent verified revocation and isolation.

### Verification independence

A fresh task prevents reliance on mutable transformer state. It does not make a shared parser implementation logically independent. Verify explicit postconditions against the sealed output, with separate verification code where practical and adversarial fixtures that test common blind spots. If a promised removal cannot be established within supported coverage, block release rather than trusting the rewrite.

Output format checks, required malware scans, confidentiality checks and fidelity checks are selected by OutputReleasePolicy. Input PASS findings do not automatically transfer to different bytes. Where a property can legitimately be reused, it must be a documented non-content property with an explicit proof of applicability; default to rechecking content-dependent requirements.

### Evidence and artifact lineage

A preparation report links input identity, eligibility policy, transformation operations, sealed output identity and output release policy. Reports keep input eligibility separate from output verdict. A transfer receipt refers to the approved output artifact, never merely the original upload. Authorized audit records may retain digests; ordinary logs exclude them to avoid unwanted identical-file correlation.

## 5 Intended use and abort safe publication

Intended use is part of the acceptance contract and is bound to the approved handle and transfer receipt. The copy request declares its destination use; the library rejects a mismatch. Receipts include artifact identity, verified length/digest, policy fingerprint, intended use and the host-provided pending-destination identifier. They are scoped transfer evidence, not portable authorization tokens.

| Use | FileGate establishes | Host must enforce |
| --- | --- | --- |
| PRIVATE_STORAGE | Required admission checks for captured content. | Access controls, retention and later use restrictions. |
| DOWNLOAD | Checks required by download policy and exact-byte transfer. | Authorization, response headers and delivery from verified storage. |
| INLINE_PREVIEW | Explicit preview profile or verified derivative. | Viewer isolation, origin policy and rendering controls. |
| EXTERNAL_SHARE | Named disclosure checks on approved original or derivative. | Recipient authorization and sharing workflow. |

Reports list host obligations separately from completed checks. DOWNLOAD never claims the library observed response headers it does not control. A host can bypass a Java library or later repurpose stored bytes; this remains a host trust assumption, not an enforceable end-to-end guarantee.

### Publication contract

copyTo writes only to a private pending destination, verifies streamed length and digest, and returns a receipt on success. The host finalizes destination writes, validates that the receipt belongs to that pending destination and intended use, then commits visibility. Any prior failure must abort. Never send a verification-in-progress copy into an already public HTTP response or visible object.

Destination adapters must define collision behavior, durability and ambiguous commit outcomes. After a connection failure during commit, reconcile using a stable operation/object identifier rather than assuming either success or rollback. Persist the approved object version or immutable identifier so later reads do not silently retrieve overwritten bytes.

### Ownership and cleanup

The caller owns input and destination streams. FileGate owns staged artifacts and its workers. Closing a result invalidates new copies and starts cleanup. A copy retains a lease until it completes; timeout-capable host I/O is required to bound that lifetime. All temporary destinations abort unless committed, including on exceptions.

Keep disk reservations charged until physical deletion succeeds. Retry failed deletion with bounded diagnostics. A janitor only touches owned files and respects active instance locks. Reconcile stale usage before admitting new work. Partition quotas across processes or require volume-level enforcement; local counters alone cannot protect a shared disk.

The host authenticates, authorizes and limits request/multipart size before framework spooling. It serves from controlled storage with deliberate filenames and headers. These remain layered upload responsibilities. [1]

## 6 Resource model and performance

### Admission without resource deadlock

Use a central resource ledger with atomic nonblocking admission. Inspect-only operations reserve an operation slot and their bounded input allowance. Preparation reserves the complete worst-case envelope before capture: input, task output, transformer scratch, sealed output copy and verification scratch. Count simultaneously existing copies; the sealed copy can overlap the transformer output.

Never hold input while waiting indefinitely for additional output quota. If a complete reservation is unavailable, return INCOMPLETE with a capacity reason before reading. If a measured implementation later acquires additional resources incrementally, acquisition must be nonblocking and failure must release owned stages. No transparent retry while retaining the same allocation.

Release each reservation only when its resource is actually gone. Enforce actual byte counts and OS scratch limits. A declared length does not reserve less than an unknown-length input can consume. Overflow-safe arithmetic is mandatory for byte totals, pixel counts and reservations.

### Bound every growth dimension

| Resource | Required boundary |
| --- | --- |
| Host memory | Fixed capture buffers; bounded policy/check metadata and evidence; no whole-file byte arrays. |
| Disk | Separate stage and scratch accounting, output ceilings, stale-file accounting and free-space headroom. |
| Workers | Fixed concurrent tasks, OS memory/CPU/process limits and bounded restart rate. |
| Parser work | Format-specific expanded bytes, entries, objects, nesting, pixels and deadlines. |
| Waiting | No unbounded admission queue; finite worker wait and result leases. |
| Transfer | Explicit byte ceiling, verified digest/length and host-supported write timeout. |

### Measure the complete cost

One capture pass is not one total read. Inspectors, scanners, sealing and release can each reread content. Report I/O amplification, host and worker RSS, CPU, allocations, stage/scratch disk, startup time and p50/p95/p99 latency. Preparation must report transformation, process startup, sealing, verification and fidelity failures separately.

Compare the same corpus and security coverage against a hand-built baseline using the same parser versions and resource envelope. Include cold/warm runs, malformed inputs, realistic file sizes, slow destinations, scanner outages and saturation. No throughput or lightweight claim is established by core JAR size alone.

Begin with sequential checks per file, disk staging and bounded buffers. Defer caches, mmap, generic execution graphs and check fanout. Set numeric production limits from measured pilot requirements; each deployment must supply a validated resource envelope. Dependency footprint and inspect-only configuration must remain independent of optional preparation.

## 7 Modules and application contract

| Artifact | Responsibility |
| --- | --- |
| filegate-core | Policy snapshots, resource leases, capture/preflight, aggregation, scoped content and verified copy. JDK-only runtime target. |
| filegate-worker | Task supervisor, bounded protocol and supported isolation runtime. Host excludes deep parser dependencies. |
| filegate-format-* | Explicit format inspectors and verification checks installed in workers. |
| filegate-prepare | Optional facade, eligibility/output policy pairing, transformation orchestration and sealed derivative handoff. |
| filegate-transform-* | Optional transformation implementations and encoder/renderer dependencies, confined to workers. |
| Optional integrations | AV and pilot-justified servlet/Spring convenience; no duplicated inspection engine. |

Start with concrete internal components and package boundaries. Extract artifacts for optional dependencies or isolation, not one interface per implementation class. Public operations use stable reason codes and immutable reports. Proposed API names below are illustrative, not existing compilable code.

```java
// gate and optional preparer are created once at startup.
try (Inspection result = gate.inspect(upload, claims)) {
    if (result instanceof Approved approved) {
        publish(approved.content(), Use.DOWNLOAD);
    } else {
        handleBlocked(result.report());
    }
}
void publish(ApprovedContent content, Use use) {
    try (PendingObject pending = storage.beginPrivate(use)) {
        TransferReceipt receipt = content.copyTo(
            pending.output(), pending.id(), use);
        pending.finishWrites();
        pending.commit(receipt);
    } // abort unless committed; reconcile ambiguous commits
}
try (Preparation result = preparer.prepare(
        upload, claims, inputEligibility, outputRelease)) {
    if (result instanceof Prepared prepared) {
        publish(prepared.content(), Use.EXTERNAL_SHARE);
    } else {
        handleBlocked(result.report());
    }
}
```

Prepared represents an APPROVED derivative; it never exposes an original-file fallback. PendingObject is a host storage adapter contract. commit validates receipt binding and publication semantics; finishWrites flushes/closes destination writes. Production integration tests verify persisted bytes, intended use, abort behavior and ambiguous commit recovery.

## 8 Format coverage and validation

### Coverage must be published per profile

PNG/JPEG profiles specify supported encodings, complete termination, structure, bounded metadata and dimensions. Explicitly define trailing-data and animation handling. If decodability is required, execute bounded isolated decoding; structural checks alone must not claim it. Pixel limits do not replace total worker-memory limits.

DOCX/XLSX profiles specify package names, normalized unique entries, supported relationships/content types and XML structures. Count actual expanded bytes, entries and repeated reads. Disable external XML resolution and DTD processing. Initially reject encryption, macros, embedded objects and external relationships unless the selected capability explicitly supports them. Avoid full workbook models when streaming structure checks suffice.

POI ZIP thresholds are global settings. Never vary them per request in a shared application JVM. Configure worker-level ceilings once, enforce request budgets independently, and reject policies the selected parser cannot enforce. [3]

PDF profiles must define supported object graphs, cross-reference/object streams, incremental revisions, encryption, actions and attachments. Use a maintained parser in the worker; raw keyword absence cannot prove feature absence. Bound recursion, objects, decoded stream bytes and work time. No blanket PDF safety claim is allowed.

### Verification required before release

| Test area | Required evidence |
| --- | --- |
| Decisions | Every missing/failed mandatory check blocks; eligibility never grants release; transformed output gets its own verdict. |
| Sealing | Late writes, retained descriptors, subprocess writers, links and unexpected outputs cannot alter verified content. |
| Resource pressure | Concurrent preparation cannot deadlock on disk; expansion/output limits, full disk and cleanup failures remain bounded. |
| Preparation | Known target structures are removed; unsupported representations block; fidelity and signature handling match policy. |
| Workers | Hangs, crashes, protocol corruption, stale replies and restart storms cannot produce false approval. |
| Publication | Wrong-use receipts, content substitution, failed copies, abandoned targets and ambiguous commits are handled explicitly. |

Maintain valid, malformed, ambiguous, truncated, encrypted and adversarial fixtures for every supported profile. Add coverage-guided fuzzing and regression capture. Independently review isolation, format checks, preparation verification and release. Tests establish observed behavior, not universal safety. Publish pinned dependencies, SBOM, licenses, supported profiles and a vulnerability-response process.

## 9 Delivery and adoption decisions

### Milestones follow evidence

| Milestone | Scope and exit evidence |
| --- | --- |
| M0 Foundation | Immutable policies, three outcomes, captured artifacts, resource leases and abort-safe verified release; failure injection passes. |
| M1 Preflight | Bounded candidate recognition and early rejection with no signature-only approval. |
| M2 Containment | Supported worker deployment and restricted PNG/JPEG milestone; demonstrate crash, timeout and resource containment. |
| M3 Adopter workflow | Complete the selected attachment workflow and required formats, including PDF/DOCX where necessary. |
| M4 Optional preparation | Only if pilot demand requires it: deterministic operations, separate policies, task termination, sealing and output verification. |
| M5 Public release | Security tests, independent review, coverage documentation and measured adoption/resource gates pass. |

M4 is not a mandatory launch dependency for inspect-only adoption. Do not delay a complete inspection workflow to build speculative editing capabilities. A preparation-led release requires the same core gates plus its own removal, fidelity and containment evidence.

### Adoption gates remain unproven

Two independent teams integrate real upload paths and continue using FileGate without extensive maintainer assistance.

Application security glue decreases materially without reducing required check coverage.

Teams accept the deployment, supported-file subset, false rejection behavior and operational support cost.

Equivalent checks fit measured latency, CPU, RSS and disk budgets.

The first release covers the whole named workflow, including storage visibility and serving obligations.

### Pilot decisions to freeze

Specify required formats and representative files; intended uses; mandatory isolation and AV; accepted rejection behavior; storage transaction semantics; and performance budgets. If preparation is needed, name exact removable structures, allowed fidelity changes and signed-document behavior. Do not invent these requirements from the architecture.

### Design position

The architecture supports a focused library with optional extensions. Its defensibility comes from the quality of its corpus, coverage contracts, integrations and operational behavior. Wider scope and more abstractions do not establish demand. The first adoption proof is a complete workflow that becomes simpler and measurably reliable for its host team.

## 10 Sources and claim boundaries

This revision incorporates the review of v0.4 and defines proposed contracts. It contains no implementation results, adoption evidence or validated performance guarantees. Sources support the external security and document-behavior statements; they do not validate FileGate itself.

[1] OWASP File Upload Cheat Sheet

https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html

Layered upload validation, controlled storage, signature limitations and host responsibilities.

[2] Apache Tika Security Model

https://tika.apache.org/security-model.html

Parser risk, isolation and the limits of MIME detection and extracted output.

[3] Apache POI ZipSecureFile API

https://poi.apache.org/apidocs/dev/org/apache/poi/openxml4j/util/ZipSecureFile.html

Global ZIP thresholds and archive resource controls.

[4] Microsoft Office Document Inspector

https://support.microsoft.com/en-us/office/collab-files/remove-hidden-data-and-personal-information-by-inspecting-documents-presentations-or-workbooks

Hidden properties and content, removal limitations and format-specific behavior.

[5] Adobe Acrobat Redaction

https://helpx.adobe.com/in/acrobat/desktop/protect-documents/redact-pdfs/redact.html

Visible redaction and optional hidden-information sanitization are separate operations.

[6] ClamAV clamd protocol manual

https://github.com/Cisco-Talos/clamav/blob/main/docs/man/clamd.8.in

Stream framing and configured scan-size limits.

Sources reviewed during the proposal review on 31 October 2026. Pin dependency versions and verify their actual capability and limit semantics during implementation. The previous C2PA reference is omitted because authenticity evaluation is outside this design’s committed scope.
