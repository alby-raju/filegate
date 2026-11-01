# Isolated image execution

`filegate-images-worker` connects `IsolatedImageCheck` to the core gate. It depends
on the worker runtime and restricted image parser; core remains JDK-only. The host
adapter does not invoke the parser. Worker implementation packages are hidden.

Construct a `LinuxContainerRuntime` with an immutable local image containing the
core, worker, image parser and image-worker JARs under `/app/`. The smoke Dockerfile
shows this layout. Runtime startup must pass the kernel containment probe. Supply
explicit worker, sandbox and image envelopes; these are deployment choices.

Create `IsolatedImageCheck(id, runtime, profile)`, compile an `ISOLATED` policy
against its descriptor, and pass the check to `FileGate.open`. The descriptor binds
the immutable image identity and all five image limits into the policy fingerprint.
The policy's input ceiling must fit the image profile; the profile input allowance
must not exceed half the scratch envelope. Keep the runtime alive for gate calls;
the caller owns its lifecycle and must close it after in-flight inspections finish.

Each call sends a fresh request ID, captured identity, exact policy fingerprint,
worker build identifier and check ID. The worker verifies containment, accepts one
bounded request and streams at most the declared length into private scratch.
It verifies EOF, length and SHA-256 before invoking PNG/JPEG inspection. Only a
single bound response is accepted. The existing supervisor enforces task timeout,
stderr/output bounds and confirmed container removal before returning evidence.
Any startup, transport, parser, timeout or termination failure blocks content.
There is no native-process or embedded fallback.

The Linux CI fixture runs valid PNG and JPEG files through the full gate, rejects
unknown bytes, and checks that closing the runtime yields incomplete evidence.
Native tests check adapter policy propagation and failure handling. These tests
are engineering evidence; broader hostile corpora, fuzzing, independent containment
review and real adopter/storage qualification remain release gates.
