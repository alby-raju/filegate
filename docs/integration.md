# Integrating the foundation

FileGate currently offers a format-independent embedded executor for explicitly
trusted checks. It has no installed production format profile, parser sandbox or
antivirus scanner. Do not use a test-only PASS check to protect uploads. A policy's
coverage is exactly the explicitly registered checks and their versions/profiles.

## Configure once

Create each `InspectionCheck` explicitly. Compile a `PolicySpec` using the
installed descriptors, then open one `FileGate` per resource domain with a private
staging parent and explicit `ResourceLimits`. Policy compilation rejects missing
checks and contradictory execution modes. Gate construction also verifies exact
descriptor equality and that the input allowance fits the staging envelope.

The current configuration is typed Java, with bounded identifiers and at most 64
mandatory checks. There is no YAML/JSON loader and no unchecked guarantee string.
Preflight checks run first; content checks are canonicalized by identifier for
this executor's deterministic sequential order. Dependencies and cost-aware
ordering belong in later inspection profiles.

`reload` accepts a fully compiled snapshot against the same installed registry.
Existing operations, reports and handles retain their previous snapshot. Adding
an installed check does not add it to a policy or change its fingerprint.

## Inspect and publish

```java
// gate is a long-lived instance configured with real, supported checks.
try (Inspection inspection = gate.inspect(uploadStream, new InputClaims(filename, mediaType))) {
    if (inspection instanceof Approved approved) {
        // Host authentication and authorization have already succeeded.
        try (LocalPendingObject pending = new LocalPendingObject(storageRoot, Use.DOWNLOAD)) {
            TransferReceipt receipt = approved.content().copyTo(pending.output(), pending.id(), pending.use());
            pending.finishWrites();
            Path immutableObject = pending.commit(receipt);
            // Persist this immutable object ID with application records.
        }
    } else {
        handleBlocked(inspection.report());
    }
}
```

The snippet uses the imports below and the POSIX adapter in `filegate-examples`:

```java
import dev.filegate.core.FileGate;
import dev.filegate.core.inspection.Approved;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.Inspection;
import dev.filegate.core.content.TransferReceipt;
import dev.filegate.core.policy.Use;
import dev.filegate.examples.LocalPendingObject;
import java.nio.file.Path;
```
[PublicationTest](../filegate-examples/src/test/java/dev/filegate/examples/PublicationTest.java)
executes this workflow under an explicitly named test-only coverage profile.

`REJECTED` establishes a policy violation. `INCOMPLETE` means mandatory evidence
could not be established. Neither exposes approved content. Reports distinguish
`NOT_RUN` from a passing check. A check exception is incomplete, not a finding that
the file is malicious. Filenames and MIME types are untrusted claims; they never
become filesystem paths.

## Ownership and resource limits

The caller owns input and destination streams; `inspect(InputStream, ...)` does
not close the source, and `copyTo` neither flushes nor closes the destination.
`inspect(Path, ...)` owns only the stream it opens and always copies the source.
FileGate owns stages. Close every inspection result. Closing it blocks new copies;
an active copy keeps its lease and reservation until it exits. Closing the gate
stops new capture but existing results and in-flight operations remain caller-owned.

Admission is nonblocking: a request reserves an operation slot and the complete
policy input allowance before reading. The reservation remains charged for an
open result, active copy or failed deletion. Use `resourceUsage()` for bounded
aggregate counters and `retryCleanup()` to retry failed deletion. No unbounded
queue, whole-file byte array or parser dependency is present in core.

Stages require POSIX owner-only directories and files. Inputs become read-only
after their capture writer closes. The host OS and same-privilege host processes
are trusted. Quotas are local to one gate: partition quotas between processes or
enforce them at volume level. Automatic crash recovery, stale-instance locks,
free-space headroom and result-lease expiry remain open roadmap items. Do not
reuse a resource budget over unreconciled stale files after a crash.

## Publication and recovery

Never copy into a public HTTP response or visible object. A failed transfer may
have written partial or invalid bytes; no receipt is returned and the host must
abort. Receipts bind streamed identity, policy, use and pending destination ID.
They are not portable authorization tokens or proof of durable persistence.

The example closes and syncs the writer, verifies the persisted pending file,
then atomically creates a unique visible hard link without replacing an existing
name. It fails if the filesystem does not support that operation. Closing the
adapter removes pending resources; a committed hard link remains. The adapter is
single-caller and requires a trusted POSIX parent on the same filesystem. It does
not establish portable directory fsync or remote-storage transaction semantics.

Persist `id()`/`objectPath()` before commit when recovery is needed. If a crash or
connection loss makes commit uncertain, reconcile that stable object name and
its stored length/digest. Do not assume either rollback or success; do not retry
under a new name. A cleanup exception after commit does not unpublish an object.
Production remote adapters need explicit version IDs, collision/durability rules
and ambiguity tests against their real storage service.

## Deadlines and claims

Generic Java streams can block despite interruption. The host must configure
request, read and destination write timeouts. Embedded checks are trusted code;
interruption is cooperative, not containment. No hard end-to-end timeout,
malware-absence, viewer-safety or de-identification claim is made. Deep hostile-file
profiles require an explicit `IsolatedInspectionCheck`. Isolated policies refuse
ordinary embedded content plugins. `filegate-images-worker` supplies
`IsolatedImageCheck`, backed only by a verified `LinuxContainerRuntime`; failures
never substitute local parsing. See [isolated images](isolated-images.md).

The host still enforces authentication, authorization, request/multipart limits
before framework spooling, serving headers, origin/viewer isolation, intended-use
restrictions and retention. Ordinary logs should avoid filenames, content digests,
parser messages and other sensitive content. Reports list host obligations
separately from completed checks.

## Bounded preflight

`PreflightCheck` accepts explicit candidate and claim rules. Built-in capture
recognizes at most eight bytes and can reject before reading the rest. Signature
candidates describe PNG/JPEG/PDF/ZIP only; ZIP does not identify DOCX or XLSX, and
no check establishes polyglot absence. A preflight-only policy cannot compile.
Claim rules are fingerprint-bound; required filenames/MIME types must be
consistent when selected. Format support still requires a real content profile.

For check implementations, use `dev.filegate.core.spi.InspectionCheck` and
`InspectionInput`. A bounded initial-prefix check can implement
`PrefixInspectionCheck`; declare its byte requirement and return evidence for its
registered check ID. The registry validates bounds at startup. The engine runs
these checks during capture through the same exception/cancellation boundary used
for full checks. See the [package map](architecture.md) for all public imports.
