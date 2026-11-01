# Repository security configuration

The repository is public under Apache 2.0. The GitHub configuration below protects
repository changes; it does not establish production readiness for FileGate.

## Default branch

The active [main-protection ruleset](https://github.com/alby-raju/filegate/rules/26315565)
targets the default branch. Its desired configuration is versioned in
[main-ruleset.json](../.github/main-ruleset.json).

- Changes require a pull request and one approving review, including the code
  owner. Review approvals become stale after new pushes, and the latest push
  needs approval by another person. Review conversations must be resolved.
- The branch must be current with its base and pass `verify (ubuntu-latest)` and
  `verify (macos-latest)`, supplied by the GitHub Actions integration (ID 15368).
- CodeQL results are required. Error-level findings and high/critical security
  findings block changes.
- History must remain linear; squash and rebase merging are enabled. Force pushes
  and deletion of the default branch are blocked.
- Repository administrators (repository role ID 5) have an explicit `always`
  bypass. Other roles, users and apps have no configured bypass. This exception
  covers every rule above and is intended for deliberate repository maintenance.

With a single code owner, that owner cannot approve their own pull request. An
administrator must deliberately use the bypass for their own changes after
checking the build and scan results. Contributors use the normal review path.
Merged development branches are deleted automatically.

To update the rules, review the JSON and apply it as an administrator:

```sh
gh api --method PUT repos/alby-raju/filegate/rulesets/26315565 \
  --input .github/main-ruleset.json
```

Verify the saved ruleset and effective rules afterward. Changing the JSON alone
does not change GitHub settings.

## Scanning and workflow permissions

| Control | Configuration |
| --- | --- |
| Secret scanning | Enabled, with push protection |
| Dependabot | Alerts and security fixes enabled; weekly Maven and Actions update PRs |
| CodeQL | Managed default setup for Java/Kotlin and Actions; default queries, remote and local input sources |
| Vulnerability reports | Private reporting enabled; see [SECURITY.md](../SECURITY.md) |
| Actions allowlist | GitHub-owned actions plus repository-local actions/workflows |
| Action versions | Full commit SHA pins required |
| Workflow token | Read permission by default; cannot approve pull requests |
| Fork pull requests | Workflow approval required for all external contributors |
| Checkout credentials | Not persisted by the verification workflow |

CodeQL is managed through GitHub default setup, rather than a second workflow in
this repository. Configuration changes should be checked in repository settings
and in the resulting analysis run. No automatic PR merging is configured.

## Generated files and verification

The ignore rules exclude Maven output, Java class files and Eclipse metadata.
[scripts/check-repository.sh](../scripts/check-repository.sh) also checks the Git
index, so force-adding generated files fails verification. These controls do not
delete local IDE files.

CI builds with JDK 21 on Linux and macOS, checks the core's JDK-only runtime
boundary, and runs the real container restrictions and cleanup tests on Linux.
[scripts/ci-verify.sh](../scripts/ci-verify.sh) retries only transient dependency
download failures, at most three attempts. Compilation and test
failures return immediately. The containment build then runs with Maven offline.
