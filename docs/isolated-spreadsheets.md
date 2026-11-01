# Isolated basic XLSX execution

`filegate-spreadsheets-worker` supplies `IsolatedSpreadsheetCheck`. Its explicitly
named `basic-xlsx-v1-poi-6.0.0` coverage is described in
[the spreadsheet profile](spreadsheet-profile.md); it is narrower than the full
XLSX product target. It is not a DOCX/PDF inspector or a production-qualified profile.

## Assembly and use

Build with `mvn clean verify`. The spreadsheet worker's package phase copies its
third-party runtime dependencies to `target/worker-libs`. Initialization clears
that generated directory to prevent stale versions from entering a later image.
Libraries remain separate JARs with their original license notices; they are not
shaded. The smoke Dockerfile includes the Office/spreadsheet/worker JARs and those
libraries under `/app/`. The aggregate SBOM inventories the Maven runtime graph.
Container OS and JDK inventory still belong to release qualification.

Open a `LinuxContainerRuntime` against that immutable local image, supplying explicit
sandbox and worker limits. Startup must pass the effective kernel probe. Create
`IsolatedSpreadsheetCheck(id, runtime, profile)`, compile an `ISOLATED` policy from
its descriptor and install the check explicitly in `FileGate.open`. The policy's
input ceiling must fit the profile and the input allowance must fit half the
scratch envelope. No production budgets are supplied by this example.

The host adapter runs no workbook parser. The common `IsolatedWorkerCheck` binds
request ID, captured bytes, policy fingerprint, build identifier and exact evidence
set. Descriptor versions use the canonical immutable image digest; coverage identity
includes every profile limit and link policy. Registry aliases do not change the
content identity. Changing the image or profile requires a matching policy.

The disposable worker verifies containment, captures and hashes the streamed input,
and performs the package, schema and basic-XLSX semantic checks. It verifies the
captured bytes again and removes them before emitting evidence. Profile violations
produce failure evidence; I/O, parser, startup, protocol, timeout or termination
uncertainty blocks approval. There is no embedded fallback. The caller owns runtime
lifecycle and closes it after in-flight calls finish.

## Host publication and limits

The Linux integration test composes the real gate and container with
`PublicationExample`. It checks exact persisted bytes, policy rejection of a
dynamic-reference formula, closed-runtime refusal and absence of extra public
objects. Worker configuration round trips every numeric limit and link policy.
Existing image-container tests guard the shared adapter/input changes.

The host remains responsible for authorization, request timeouts, serving headers,
filesystem permissions and crash/ambiguous-commit recovery. Formula inspection does
not calculate results, authenticate cached values, bound desktop calculation costs,
remove confidential content or establish malware absence. POI's additional ZIP
protections remain enabled. Representative-file rejection rates and production
performance still require adopter qualification.
