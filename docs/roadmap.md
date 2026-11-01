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
| M0.3 | Scoped inspection facade, sequential embedded checks and content leases | Only complete evidence grants a handle; inspector failures incomplete; close blocks new copies; active copies retain storage; no public stage path | Complete |
| M0.4 | Verified release, receipt binding and abort-safe local storage example | Tampering, truncated/failed copies, wrong use/destination, abandoned targets, finalization and commit failures; persisted bytes verified | Complete |
| M0.5 | Integration docs and CI | Executable workflow example; JDK-only packaged runtime; clean build on Java 21; documented host obligations and timeout limits | Complete |
| M1.1 | Bounded candidate recognition and claim policy | PNG/JPEG/PDF/ZIP candidates, short/truncated/unknown input, spoofed claims; signatures never substitute for required structure checks | Complete |
| M1.2 | Preflight integration and corpus | Capture-time envelope rejection, `NOT_RUN` evidence for skipped checks, ambiguous candidates; no format approval until supported inspectors exist | Complete |
| M2.1 | Worker artifact and bounded protocol | Frame/evidence/stderr limits; request/digest/policy/build binding; stale, missing, duplicate and malformed responses block | Complete |
| M2.2 | Fixed worker pool and lifecycle | One task per worker; bounded wait; crash/timeout termination; cancellation; restart backoff and saturation tests | Complete |
| M2.3 | Supported Linux containment | Non-root, read-only code/input, no credentials/network, bounded memory/CPU/process/scratch; deployment-level escape and kill tests; no embedded fallback | Implemented; Linux smoke validated, full security qualification open |
| M2.4 | Restricted PNG/JPEG inspectors | Published encoding/termination/trailing-data/animation/metadata/dimension coverage; bounded isolated decoding if promised; malformed corpus and fuzz regression | Inspector implemented; isolated integration and broader corpus pending |
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

## Current implementation boundary

M0 foundation and M1 bounded preflight are implemented. M2 protocol, task
supervision and Linux runtime are implemented; the Linux containment smoke test
passes in CI. The restricted PNG/JPEG inspector has local regression coverage,
but is not connected to the isolated gate executor yet. Stop at this checkpoint
for the user-requested repository cleanup and enhancements before starting another
implementation slice. Remaining M2–M5 work stays open, including PDF/DOCX, optional
preparation, storage qualification, security review and adoption evidence.

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

- M0.3: `mvn verify` passes 22 tests. The embedded facade closes inspector streams,
  stops on denial/error, reports skipped checks and exposes content only after
  complete evidence. Path inputs are copied. Reloads preserve in-flight snapshots;
  leases retain storage after owner close. Unsupported isolated execution fails
  at construction. Existing results remain caller-owned after closing the gate.

- M0.4: `mvn verify` passes 32 tests across core and host examples. Transfers
  detect replaced/truncated/extended stages, bind use/destination receipts and keep
  active-copy leases through result close. The POSIX local example verifies
  finalized persisted bytes and publishes with atomic no-replace hard linking;
  abandon, wrong receipt, finalization failure and collision tests pass. Cloud
  transaction and crash-durability semantics remain adapter-specific M3/M5 gates.

- M0.5: integration and recovery contracts documented, workflow exercised by
  `PublicationTest`, and `jdeps` reports only `java.base` for core. Immutable-action
  CI added for Linux/macOS Java 21; remote results are pending the next push.
  M0 foundation is implemented; crash/volume recovery and real storage adapter
  qualification remain explicit M3/M5 gates.

- M1.1: `mvn verify` passes 36 tests. Offset-zero bounded recognition distinguishes
  PNG/JPEG/PDF/ZIP candidates only. ZIP claims do not establish DOCX/XLSX package
  type. Claim rules are fingerprint-bound, and preflight-only policies fail
  compilation. Adding capability kinds versions the canonical policy encoding.

- M1.2: `mvn verify` passes 40 tests. Built-in preflight runs on at most eight
  prefix bytes during capture, ahead of content checks, and records early denial
  plus `NOT_RUN` checks. Passing recognition still requires full capture and all
  content evidence. Unknown/truncated/spoofed and short-stream fixtures pass.

- CI: [run 41295133856](https://github.com/alby-raju/filegate/actions/runs/41295133856)
  passes on Linux and macOS for commit `9442727`.
- M2.1: `mvn verify` passes 46 tests total. The separate JDK-only worker artifact
  implements versioned 32 KiB frames, complete binding and exact bounded evidence
  sets. Six protocol tests include 500 deterministic random malformed frames.
  This is transport framing, not sandbox containment or a production inspector.

- M2.2: `mvn verify` passes 52 tests, including real disposable subprocess faults.
  Worker slots and I/O queues are fixed, admission is nonblocking, deadlines kill
  hung tasks, diagnostic bytes are capped and restart backoff prevents storms.
  Unconfirmed termination quarantines the domain. Native JVM tests require host
  process enumeration; a subprocess is not containment and this pool is not yet
  accepted by the core as an isolated production executor.

- M2.3 implementation: explicit immutable-image Linux runtime and effective
  cgroup/mount/network probe added. Each task gets a disposable container and
  confirmed removal; no host mounts or credentials are passed. Local tests skip
  two actual-container tests by design. Linux CI now builds a digest-bound fixture
  and must pass those tests before containment is recorded as validated. See
  [worker deployment limits](linux-workers.md).

- Worker lifecycle fix: a deterministic EOF-before-exit fixture now verifies
  crash classification after waiting for natural process exit. The full native
  suite passes 55 tests with two Linux-container tests skipped on macOS. Container
  restart backoff is applied before creation as well as before attached execution.

- Linux CI: [run 41296517190](https://github.com/alby-raju/filegate/actions/runs/41296517190)
  passes on commit `22246d3`, including the actual-container restriction probe and
  timeout cleanup. Full containment qualification remains open.
- M2.4 inspector checkpoint: `mvn verify` passes 61 native tests, with two Linux-only
  container tests skipped locally. Restricted PNG/JPEG structure and JDK decoding
  regressions pass; no isolated-gate integration or production approval is claimed.
- User requested stopping after the current implementation for repository cleanup
  and enhancements. This checkpoint records the image inspector without beginning
  the next isolated integration slice.

- Repository enhancement 1: Apache 2.0 selected. Added the unchanged official
  license, project NOTICE and full source-header template with the current author
  attribution. Maven metadata declares the license and every artifact packages
  LICENSE/NOTICE under META-INF. Remaining release gates are unchanged.

- Licensing validation: Maven `package` succeeds with tests skipped for this
  comment/metadata change. All 65 Java bodies match the previous commit exactly;
  header/XML/link checks pass and all four JARs contain byte-for-byte root LICENSE
  and NOTICE copies. No feature implementation was resumed.

- Architecture cleanup 1: core contracts now use capability packages, while
  capture, resource and capability implementations are not exported. Sealed public
  handles preserve issuance control. Shared test fixtures replace test-class
  coupling. The migrated reactor passes 63 native tests; two Linux-only tests are
  skipped locally. External client compilation verifies module encapsulation,
  and `jdeps` still reports `java.base` only.

- Architecture cleanup 2: FileGate delegates to immutable bound plans, an
  inspection engine and a sequential runner. Capture byte processing, prefix
  state and filesystem ownership are separate. Typed capture outcomes replace
  nullable tuples; one invocation boundary translates check failures. The bounded
  prefix SPI supports independent plugins without concrete preflight dependencies.
  Policy encoding is isolated without changing its format. All 66 native tests
  pass; two Linux-only tests remain intentionally skipped locally.

- Architecture cleanup 3: worker protocol, process supervision and Linux runtime
  have separate exported packages. Stream exchange, monotonic deadlines, process
  termination and restart backoff are hidden collaborators; Docker argument
  construction and daemon control are distinct from container ownership. Worker
  outcomes are typed, and test fixtures no longer depend on test classes. Shared
  Maven test configuration and JUnit version are declared once. A clean reactor
  build passes all 66 native tests; core still requires only `java.base`.

- Architecture cleanup 4: restricted image facade delegates to separate PNG,
  JPEG, zlib-scanline and JDK-decoder components. Geometry budgets are shared;
  parsers and decoder are hidden by the image module. The unused image-to-worker
  dependency is removed. JPEG frame/table/scan checks have named operations, and
  decoded storage is released on blocking warnings as well as success. The
  existing regression suite still passes all 66 native tests.
- Refactored worker CI: [run 41300740992](https://github.com/alby-raju/filegate/actions/runs/41300740992)
  passes on Linux and macOS, including actual container restrictions and timeout
  cleanup for commit `211b0c5`.

- Architecture guardrails: package/dependency and responsibility maps plus source
  migration notes are documented in `docs/architecture.md`. CONTRIBUTING records
  ownership, extension and test boundaries. A pinned AOSP Java formatter is checked
  during Maven validation. `clean verify` passes all 66 native tests; all 100 Java
  copyright headers and all packaged LICENSE/NOTICE files are preserved.
