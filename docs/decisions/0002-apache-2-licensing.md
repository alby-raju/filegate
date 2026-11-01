# Apache 2.0 licensing and source attribution

Date: 1 November 2026

Status: accepted.

## Decision

Publish FileGate's original code under the Apache License, Version 2.0, as selected
by the project owner. Keep the official license text unchanged in the root
`LICENSE` file and record project attribution in `NOTICE`.

Use `Copyright 2026 alby-raju`, matching the repository's existing Git author
identity. The source header includes FileGate's name and purpose, an SPDX
identifier and the full Apache license notice. It adds no restrictions to the
standard license. Apply the header to Java source and tests, Maven POMs, workflow
configuration and Dockerfiles. Keep the reusable Java template in
[license-header.txt](../license-header.txt).

Follow the [Apache application guidance](https://www.apache.org/foundation/license-faq.html).
Keep existing third-party copyright and attribution notices when incorporating
third-party material; this project header does not replace their notices.

## Distribution

The parent Maven POM declares Apache 2.0 and packages the root `LICENSE` and
`NOTICE` files unchanged under `META-INF` in each artifact JAR. The resource path
assumes the current layout, with all artifact modules directly under the root.
Preserve normal `src/main/resources` handling when adding other resources.

This decision resolves the license choice left open in the
[repository foundation](0001-repository-foundation.md). Publication coordinates,
dependency attribution, SBOM and the remaining release gates are still separate
roadmap work.
