# Contributing to FileGate

Read the [architecture](docs/architecture.md), [coverage](docs/image-profile.md)
and [roadmap](docs/roadmap.md) before changing acceptance or lifecycle behavior.

## Build and format

Use JDK 21 and Maven 3.9 or newer.

```sh
mvn spotless:apply
mvn clean verify
```

The parent POM pins the formatter, build plugins and shared JUnit version. Java
uses AOSP formatting with four-space indentation. Formatting is checked during
`verify`. Tests requiring Linux containers run in Linux CI; they are skipped on a
native environment without the explicitly configured fixture image.

## Code boundaries

- Put code in the package that owns the capability. Public policy and evidence
  remain independent of storage, worker protocols and parser implementations.
- Keep core dependent only on the JDK. Format dependencies belong in their own
  artifacts; optional preparation must not become a core dependency.
- Keep mutable state with its lifecycle owner. An immutable value should not also
  launch processes, own streams or manipulate files.
- Give methods one clear task and name the condition or operation they implement.
  Extract shared rules within their owning component; avoid a general utilities
  package or interfaces with no actual substitution boundary.
- Preserve scoped ownership, missing-evidence blocking, verdict precedence,
  actual-byte bounds, policy binding and private publication. No fallback may
  convert uncertainty into approval.
- Keep implementation packages non-exported. Do not add public constructors that
  manufacture approval, expose stage paths or bypass verified transfer.

## Tests and changes

Test observable contracts and failure boundaries. Use dedicated test fixtures,
not helpers on other test classes. Cover cancellation, cleanup and ownership when
changing resource lifecycles. Run Linux CI for process/container changes and keep
format regressions when changing parsers. Separate behavioral refactors from new
coverage and document pre-release API changes.

Keep commits focused and update architecture or integration guidance when a public
boundary changes. Publish a supported profile only after its declared acceptance
evidence exists.

## License headers

Use the [full project header](docs/license-header.txt) for first-party source and
preserve existing attribution. Keep third-party notices intact. New dependencies
need a justified boundary, a pinned version and attribution appropriate to their
distribution. LICENSE and NOTICE must remain included in artifact JARs.
