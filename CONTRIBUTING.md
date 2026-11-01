# Contributing to FileGate

Read the [architecture](docs/architecture.md), [coverage](docs/image-profile.md)
and [roadmap](docs/roadmap.md) before changing acceptance or lifecycle behavior.

## Build and format

Use JDK 21 and Maven 3.9 or newer.

```sh
mvn clean verify
scripts/check-repository.sh
```

The parent POM pins build plugins and the shared JUnit version. Preserve the
existing Eclipse formatting, including tabs and import grouping. The incompatible
Google Java Format check has been removed. A shared Eclipse formatter profile
must be exported from the IDE before automated formatting is reintroduced.
Tests requiring Linux containers run in Linux CI; they are skipped on a
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

## Repository hygiene and review

Maven output (`target/`), IDE compiler output (`bin/`, `out/`), `.class` files and
Eclipse project settings are local files. Ignore them; do not force-add them.
CI checks the Git index independently of `.gitignore`. An IDE staging view can be
stale: use `git status --short` to inspect the actual index.

The main branch requires a pull request, owner review, resolved conversations and
successful Linux/macOS verification. Administrators have an explicit bypass for
repository maintenance. Use normal pull requests for routine work and reserve the
bypass for deliberate maintenance. The [repository security configuration](docs/repository-security.md)
documents the active controls and the versioned ruleset. See [the security policy](SECURITY.md) for
private vulnerability reporting. Dependabot proposes Maven and Actions updates;
updates still pass the same review and verification gates.

CI retries at most three times when Maven reports a transient artifact-download
failure (HTTP 429/502/503/504 or a connection timeout/reset). It fails immediately
on compilation, formatting or test failures. The Linux containment pass runs
offline against the dependencies already resolved by the first build.
