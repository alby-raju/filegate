# Distribution preparation

`mvn verify` builds the artifact JARs. Generate the aggregate Maven runtime SBOM:

```sh
mvn -Psbom org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
python3 scripts/check-distribution.py
```

The opt-in profile pins CycloneDX Maven Plugin 2.9.3 and writes CycloneDX 1.6 JSON
to `target/bom.json`. It includes reactor artifacts and their compile/runtime
inventory and excludes test dependencies. Generation does not publish anything.
CI verifies every module appears and every artifact packages the exact project
LICENSE and NOTICE, then uploads the JSON as `maven-runtime-sbom` for 30 days.
Generated JARs and SBOMs stay outside Git.

This inventory covers Maven artifacts. It is not an inventory of the build-tool
chain, JDK, native runtime libraries or container OS. Production distribution must
also inventory and scan the exact immutable worker image and tie it to the release.
No Maven Central publication or signed release has been made. The provisional
`dev.filegate` coordinates still need ownership verification and a publication
identity before distribution. Supported profiles, independent review, adopter
qualification and measured performance remain release gates.
