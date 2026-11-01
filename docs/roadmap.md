# Implementation roadmap

Source: [proposal revision 0.5](design/proposal.md), whose original filename says
v0.4. This roadmap translates its contracts into implementation commits. Future
milestones remain conditional on their acceptance evidence and pilot decisions.

## Working agreement

Each slice gets its own commit after focused tests and `mvn verify` pass. Update
this file with observed evidence in the same commit. Keep core runtime JDK-only;
put parser and transformation dependencies in their explicitly installed modules.
A passing test suite is evidence for the tested behavior, not a universal safety
claim. No slice may approve content with missing mandatory evidence.

## Commit sequence

| Slice | Scope | Acceptance evidence | Status |
| --- | --- | --- | --- |
| R0 | Record this plan and proposal traceability | Review requirements against proposal sections 1–9 | Complete |
| M0.1 | Immutable policies, explicit versioned checks, three outcomes and immutable evidence | Unknown/missing capabilities rejected; canonical fingerprints stable; missing/duplicate/foreign evidence blocks; rejection precedence | Complete |
| M0.2 | Atomic resource admission and private bounded capture | Denial before reading; concurrent quota limits; actual byte counting; exact limit/limit+1; digest; source ownership; I/O and deletion failures | Complete |
| M0.3 | Scoped inspection facade, sequential embedded checks and content leases | Only complete evidence grants a handle; inspector failures incomplete; close blocks new copies; active copies retain storage; no public stage path | Next |
| M0.4 | Verified release, receipt binding and abort-safe local storage example | Tampering, truncated/failed copies, wrong use/destination, abandoned targets, finalization and commit failures; persisted bytes verified | Planned |
| M0.5 | Integration docs and CI | Executable workflow example; JDK-only packaged runtime; clean build on Java 21; documented host obligations and timeout limits | Planned |
| M1.1 | Bounded candidate recognition and claim policy | PNG/JPEG/PDF/ZIP candidates, short/truncated/unknown input, spoofed claims; signatures never substitute for required structure checks | Planned |
| M1.2 | Preflight integration and corpus | Capture-time envelope rejection, `NOT_RUN` evidence for skipped checks, ambiguous candidates; no format approval until supported inspectors exist | Planned |
| M2.1 | Worker artifact and bounded protocol | Frame/evidence/stderr limits; request/digest/policy/build binding; stale, missing, duplicate and malformed responses block | Planned |
| M2.2 | Fixed worker pool and lifecycle | One task per worker; bounded wait; crash/timeout termination; cancellation; restart backoff and saturation tests | Planned |
| M2.3 | Supported Linux containment | Non-root, read-only code/input, no credentials/network, bounded memory/CPU/process/scratch; deployment-level escape and kill tests; no embedded fallback | Requires Linux validation environment |
| M2.4 | Restricted PNG/JPEG inspectors | Published encoding/termination/trailing-data/animation/metadata/dimension coverage; bounded isolated decoding if promised; malformed corpus and fuzz regression | After M2.3 |
| M3.1 | Freeze one adopter contract | Formats, intended use, AV, budgets, rejection tolerance, storage transaction semantics and representative corpus approved by adopter | Requires pilot decisions |
| M3.2 | Complete required format coverage | PDF and/or DOCX/XLSX only if required; parser budgets and prohibited feature checks; no raw-keyword safety claims; pinned worker dependencies | After M3.1 |
| M3.3 | Required AV integration | Scan exact artifact; configured size/coverage limits; outages and uncertain results block; no implicit dependency activation | If pilot requires AV |
| M3.4 | End-to-end host integration | Authorization and upload limits before spooling; private pending writes; immutable stored object identity; abort and ambiguous-commit recovery | Requires storage adapter contract |
| M4.1 | Optional preparation facade and policy pair | Separate eligibility/output fingerprints and verdicts; eligibility grants no content; full envelope reserved before capture | Only if pilot requires preparation |
| M4.2 | One named transformation | Exact removable structures, supported representations, signed-file handling and fidelity contract; unsupported constructs block | Requires named operation and corpus |
| M4.3 | Sealed handoff and fresh verification | Process-tree exit; retained descriptors/late writers/links/unexpected files; host-owned copy; output checks and scans rerun; no original fallback | After M4.2 |
| M5.1 | Adversarial and operational qualification | Full disk, cleanup/janitor recovery, stale locks, shared-volume quotas, worker storms, slow I/O; fuzzing and independent security review | Release gate |
| M5.2 | Measured adoption and performance | Two independent adopters; equivalent-coverage baseline; cold/warm p50/p95/p99, I/O amplification, CPU/RSS/disk and preparation costs | Release gate |
| M5.3 | Distribution | Supported profiles and claim boundaries, SBOM, pinned dependencies, license, publication identity and vulnerability-response process | Release gate |

M4 is optional for an inspection-only launch. Do not implement speculative
redaction, OCR, conversion, a malware engine, generic workflow graphs, caches or
per-check thread pools. PNG/JPEG is an engineering milestone, not automatically
a complete adopter workflow.

## Proposal traceability

| Proposal sections | Implementation slices |
| --- | --- |
| 1 Outcomes, policy compilation and scope | M0.1, M3.1, M5.3 |
| 2 Capture, inspection, isolation and deadlines | M0.2–M0.3, M1, M2 |
| 3 Optional preparation and distinct policies | M4.1–M4.2 |
| 4 Termination, sealing, independent verification and lineage | M4.3 |
| 5 Intended use, pending publication, ownership and cleanup | M0.3–M0.5, M3.4, M5.1 |
| 6 Resource ledger, complete envelopes and measurements | M0.2, M2.3, M4.1, M5.1–M5.2 |
| 7 Module and application contracts | M0.1–M0.5, M2.1, M4.1 |
| 8 Coverage, adversarial tests and dependency hygiene | M1–M5 |
| 9 Adoption decisions and release gates | M3.1, M5.2–M5.3 |

## Current execution boundary

Implement the format-independent M0 foundation first, then bounded M1 preflight.
M2 containment needs a supported Linux validation environment. M3 and M4 need
adopter decisions; no production limits, formats, AV guarantees or transformation
operations will be invented from the design. Record limitations as open gates,
not completed features.

## Evidence log

- Repository scaffold builds on Java 21; no behavior was implemented before R0.
- R0: detailed commit sequence and proposal traceability recorded.

- M0.1: `mvn verify` passes 6 tests covering canonical/version-bound fingerprints,
  snapshot immutability, absent/incompatible capabilities, incomplete evidence and
  rejection precedence. Typed policy input cannot silently ignore unknown keys.
- Scope update: user requested all offered workflows: PNG/JPEG, PDF/DOCX and mixed
  attachments. Concrete deployment budgets and preparation operations remain open.

- M0.2: `mvn verify` passes 14 tests total. Capture uses private POSIX staging, a
  fixed buffer, actual byte limits and SHA-256. Quotas reject without reading;
  concurrent and overflow-edge reservations stay bounded. Failed deletion retains
  the charge until explicit retry. Crash recovery/shared-volume enforcement remain
  M5 gates; this stage store requires a POSIX filesystem.
