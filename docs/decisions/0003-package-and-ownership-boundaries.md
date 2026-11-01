# Package and ownership boundaries

Date: 1 November 2026

Status: accepted for the pre-release architecture refactor.

## Problem

The initial implementation put public policy, inspection, content, resource and
preflight contracts alongside their implementation in one package. FileGate also
assembled dependencies and executed checks, staging mixed byte processing with
resource cleanup, and one image class parsed both formats. Tests borrowed helpers
from other test classes. These choices made responsibilities and ownership harder
to follow and would force unrelated changes into the same classes.

## Decision

Organize core contracts by capability: `policy`, `inspection`, `spi`, `content`,
`resource` and `preflight`. Keep the `FileGate` entry point at the core root.
Implementation packages under `internal` are not exported by the Java module.
Grouping follows responsibilities rather than technical buckets such as `model`,
`manager`, `service` or a shared `utils` package.

Approval results, inspector input, approved content and transfer receipts are
sealed contracts. Their issuing implementations live in hidden packages. Moving
packages must not require public constructors that let callers manufacture
approval or obtain stage paths. Reports and policy definitions remain immutable
values. The host and JVM classpath are trusted; named-module boundaries provide
supported consumer encapsulation, not a sandbox against a malicious host.

Use concrete internal collaborators where there is one implementation. Introduce
an interface only for a real boundary: inspection plugins, bounded prefix checks,
and caller-owned content access. Keep immutable configuration and explicit
startup assembly; do not introduce a dependency injection framework, service
locator, generic workflow engine or an interface for every class.

Separate policy fingerprint encoding, check registration, execution, bounded byte
capture and filesystem cleanup. Keep PNG parsing, JPEG parsing and decoder
selection separate. Keep worker protocol, process supervision and Linux container
control separate. Centralize shared operations within their owning component,
without making the parser or worker depend on core implementation packages.

Tests own explicit fixtures instead of calling methods on other test classes.
Consumer compilation tests check that public contracts are accessible through the
named module while capture and capability implementations are not exported.
Existing failure and ownership tests remain the behavior contract.

## Compatibility

This is a pre-release source API change: callers must import the new capability
packages. Artifact names and Java 21 baseline remain unchanged. Update examples
and integration guidance in the same refactor. Preserve policy fingerprint and
worker wire encodings; package names do not belong in either contract.

## Implementation sequence

1. Separate core contracts and hidden implementations; migrate dependents and
   test fixtures, and verify module visibility with actual consumer compilation.
2. Reduce the facade to assembly/delegation; separate bounded capture, check
   execution and ownership; replace concrete preflight coupling with a bounded SPI.
3. Split worker protocol/process/Linux packages and lifecycle collaborators.
4. Split image parsers and decoder concerns; document the final dependency map
   and verify the entire reactor and Linux containment CI.
