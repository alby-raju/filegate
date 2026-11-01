# FileGate

FileGate is a proposed Java library for policy-based attachment inspection and
verified release of approved bytes. Optional preparation will produce a separate
derivative and require its own release checks.

The foundation implements policy compilation, bounded private capture, sequential
trusted embedded checks, immutable reports and scoped approved-content handles.
Verified transfers bind receipts to destination IDs and uses; the host example
checks persisted bytes before publication. Isolation and preparation are still
under development. No
production format profiles are installed; it is not ready to protect uploads.

## Build

Requirements: JDK 21 and Maven 3.9 or later.

```sh
mvn verify
```

The reactor contains `filegate-core` and a separate `filegate-examples` host
adapter. Core requires only `java.base`; JUnit is test-only. Tests exercise policy,
capture, resource and publication failures. No production format coverage is
claimed. CI builds on Linux and macOS with Java 21.

See the [integration guide](docs/integration.md) for API use, ownership, deployment
limits and host publication obligations.

## Design

- [Design proposal](docs/design/proposal.md): searchable transcription of the
  supplied document, whose contents identify revision 0.5 dated 1 November 2026.
- [Original proposal](docs/design/source/FileGate_Design_Proposal_v0.4_Protected_File_Lifecycle.docx):
  unchanged source; its filename identifies v0.4.
- [Implementation roadmap](docs/roadmap.md): milestones and the first development slice.
- [Initial decisions](docs/decisions/0001-repository-foundation.md): build and module choices.

The proposal is design input, not evidence that the described guarantees exist.

## Core contracts to implement

- `APPROVED`, `REJECTED` and `INCOMPLETE` are distinct outcomes. Missing mandatory
  evidence blocks approval; rejection takes precedence over incompleteness.
- Inspection operates on privately captured bytes under an immutable policy
  snapshot and a bounded resource reservation.
- Approved content has scoped ownership. Release verifies length and digest into
  a private pending destination; the host controls publication and abort.
- Deep format inspection belongs in explicitly configured isolated workers.
- Preparation is optional, uses separate eligibility and release policies, and
  requires sealing and verification of new bytes before release.

## Repository structure

```text
filegate-core/    JDK-only policy, capture, inspection and verified transfer
filegate-examples/ POSIX host publication example
docs/design/     Original proposal and text transcription
docs/decisions/  Recorded implementation decisions
docs/roadmap.md  Work sequence and acceptance evidence
```

Worker, format and preparation artifacts will be added when their implementation
begins. The provisional Maven coordinates are `dev.filegate:*:0.1.0-SNAPSHOT`;
publication coordinates and licensing must be settled before distribution.
