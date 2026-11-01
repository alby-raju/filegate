# Repository foundation

Date: 1 November 2026

Status: accepted for the initial scaffold; publication identity remains provisional.

## Context

The supplied proposal describes a Java 21, framework-independent blocking library.
Core runtime dependencies must remain independent of deep parsers and optional
transformation tooling. The immediate task is to create the repository.

## Decision

Use a Maven reactor with a single initial `filegate-core` artifact and Java 21
release compilation. Maven is a repository setup choice, not a requirement of the
proposal. Pin build plugin versions and use no third-party runtime dependencies.

Use a Java module descriptor with no exported API until the M0 contracts are
implemented. Add worker and optional artifacts when real isolation or dependency
boundaries require them. Avoid placeholder public inspection methods that could
be mistaken for working protection.

Retain the source DOCX unchanged and a searchable Markdown transcription. Track
design requirements separately from implemented behavior and acceptance evidence.

Initialize Git locally on `main`. No hosting provider, remote visibility, license,
or public package namespace has been selected. `dev.filegate` is provisional and
does not assert ownership of a domain or artifact repository namespace.

## Consequences

The first build checks repository wiring and Java compilation only. No security,
performance, isolation or adoption claims follow from it. M0 will introduce the
first behavior and meaningful tests. A license and publication coordinates are
required before distributing releases.
