# FileGate

FileGate is a proposed Java library for policy-based attachment inspection and
verified release of approved bytes. Optional preparation will produce a separate
derivative and require its own release checks.

The foundation implements policy compilation, bounded private capture, sequential
trusted embedded checks, immutable reports and scoped approved-content handles.
Verified transfers bind receipts to destination IDs and uses; the host example
checks persisted bytes before publication. A separate Linux container runtime and
restricted image inspector are connected through an explicit isolated adapter.
No production format profiles are installed by default; it is not yet qualified to
protect uploads. A restricted basic XLSX inspector and explicit isolated adapter are implemented.
PDF/DOCX acceptance, broader XLSX features and optional preparation remain planned. The shared Office
ZIP/XML inventory boundary is implemented; it does not grant document approval.

## Build

Requirements: JDK 21 and Maven 3.9 or later.

```sh
mvn clean verify
```

Core requires only `java.base`; JUnit is test-only. Separate artifacts contain the
worker runtime, restricted images and a host publication example. Tests exercise
policy, capture, resource, worker, image and publication failures. CI builds on
Linux and macOS with Java 21, and Linux runs actual container restriction tests.

See the [architecture and package map](docs/architecture.md) and
[contributing guide](CONTRIBUTING.md) for code boundaries and formatting.
See the [integration guide](docs/integration.md) for API use, ownership, deployment
limits and host publication obligations.

## Design

- [Design proposal](docs/design/proposal.md): searchable transcription of the
  supplied document, whose contents identify revision 0.5 dated 1 November 2026.
- [Original proposal](docs/design/source/FileGate_Design_Proposal_v0.4_Protected_File_Lifecycle.docx):
  unchanged source; its filename identifies v0.4.
- [Implementation roadmap](docs/roadmap.md): milestones and the first development slice.
- [Initial decisions](docs/decisions/0001-repository-foundation.md): build and module choices.
- [Linux workers](docs/linux-workers.md): deployment restrictions and containment evidence.
- [Image coverage](docs/image-profile.md): supported subset, limits and remaining integration work.

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
filegate-worker/  Bounded protocol, process supervision and Linux container runtime
filegate-format-images/ Restricted PNG/JPEG inspector
filegate-images-worker/ Linux-isolated image adapter and disposable worker
filegate-format-office/ Bounded OOXML ZIP/XML inventory and manifest checks
filegate-format-spreadsheets/ Restricted basic XLSX semantics and formula policy
filegate-spreadsheets-worker/ Explicit Linux-isolated basic XLSX adapter
filegate-examples/ POSIX host publication example
docs/design/     Original proposal and text transcription
docs/decisions/  Recorded implementation decisions
docs/roadmap.md  Work sequence and acceptance evidence
```

Document format and preparation artifacts will be added as their implementation
begins. The provisional Maven coordinates are `dev.filegate:*:0.1.0-SNAPSHOT`;
publication coordinates must be settled before distribution.

## Security and contributions

Report vulnerabilities through [private reporting](SECURITY.md). Development and
review conventions are in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Copyright 2026 alby-raju. FileGate is licensed under the
[Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for project attribution
and the [source header template](docs/license-header.txt) for new files.
