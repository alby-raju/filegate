# Implementation roadmap

The proposal's milestones are planned work. Only the repository scaffold is
complete. No attachment inspection or release behavior is implemented.

## M0 Foundation

Implement the following in small reviewable slices:

1. Immutable policy snapshots, explicit required check identifiers, intended use,
   stable reason codes and three outcomes. Approval requires complete evidence;
   definite rejection takes precedence over incomplete evidence. Define canonical
   policy fingerprints and reject invalid configuration before serving traffic.
2. Atomic nonblocking resource admission and private bounded capture. Count actual
   bytes, calculate a digest while capturing, close the writer before inspection
   and keep stage paths private. Use overflow-safe quota arithmetic.
3. Scoped results and artifact leases. Closing a result blocks new copies while
   active copies retain their leases. Keep disk reservations charged until owned
   files are physically deleted; exercise deletion failure and reconciliation.
4. Verified copy into a private pending destination. Bind receipts to exact bytes,
   policy, intended use and pending destination ID. Document host finalization,
   commit, abort and ambiguous-commit recovery responsibilities.

Acceptance evidence must include missing evidence, contradictory policies,
oversized input, capacity denial before reading, capture failures, cleanup
failures, close/copy races, destination failure, byte substitution and wrong-use
or wrong-destination receipts. Approval tests must use an explicitly declared test
coverage profile; they must not imply production format support.

**Next development slice:** define the outcome/evidence model and policy compiler,
with focused tests for fail-closed aggregation and invalid policy construction.
Then implement resource admission and bounded capture against that contract.

## Later milestones

| Milestone | Deliverable and exit evidence |
| --- | --- |
| M1 Preflight | Bounded candidate recognition and early rejection; signatures alone never approve a format. |
| M2 Containment | Supported Linux worker isolation and restricted PNG/JPEG profiles, with crash, timeout and resource containment evidence. |
| M3 Adopter workflow | A complete named attachment workflow, including PDF/DOCX if required by that adopter. |
| M4 Optional preparation | Only with pilot demand: named deterministic operations, separate input/output policies, writer termination, sealed derivatives and fresh verification. |
| M5 Public release | Published coverage, adversarial tests, independent review, dependency/license inventory and measured operational and adoption gates. |

## Decisions needed before the pilot

- Required formats, representative corpus and intended uses.
- Required isolation and antivirus coverage, plus accepted blocking behavior.
- Storage adapter semantics, including abort and ambiguous commits.
- Measured limits for input, scratch, concurrency, worker resources and latency.
- If preparation is needed, exact target structures, fidelity expectations and
  signed-document behavior.

These are open product and deployment decisions. They do not block the local
repository scaffold or the format-independent M0 contracts.
