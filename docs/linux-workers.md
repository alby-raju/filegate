# Linux worker containment

The worker artifact now contains a local Linux Docker/cgroup-v2 runtime. It is
not yet installed as the core's isolated executor. The core still refuses
isolated policies until a real format executor is connected. Native process
supervision alone is not a sandbox.

## Supported environment

Use Linux with a local Docker daemon at `/var/run/docker.sock`, cgroup v2,
permission to create containers and an approved immutable worker image. macOS,
remote daemons, mutable image tags, root task identities and embedded fallback
are refused. Host Docker access is privileged and belongs only to the trusted
supervisor; no Docker socket, host directory, secret or credential is mounted in
a worker. The empty owner-only CLI configuration prevents reading host registry
credentials. Images must already exist locally; task launch never pulls images.

`ContainerSpec` fixes a non-root UID/GID, read-only root, disabled network,
dropped capabilities, no-new-privileges, private IPC/cgroup namespace, memory and
swap limits, CPU quota, PID ceiling, file-descriptor/core limits and a bounded
noexec/nosuid/nodev tmpfs. These flags follow Docker's documented
[run controls](https://docs.docker.com/reference/cli/docker/container/run/) and
[resource constraints](https://docs.docker.com/engine/containers/resource_constraints/).

`LinuxContainerRuntime.open` actually starts the image's
`LinuxContainmentProbe`. Before traffic, it checks effective UID/capabilities,
NoNewPrivs, numeric cgroup memory/swap/CPU/PID limits, read-only root, restricted
scratch mount/size and absence of non-loopback network interfaces. Format worker
entrypoints must repeat the probe before parsing each task. The image and its
entrypoint are trusted installation inputs; the probe is observed deployment
evidence, not proof against kernel vulnerabilities or every possible escape.

## Task lifecycle

Runtime admission is nonblocking and bounded before container creation. Each
slot gets a disposable container, one framed request and captured input streamed
over stdin. No host stage path is exposed or mounted. A format worker must cap,
capture and hash this payload inside its scratch allowance before parsing, and
bind its response to the complete request identity.

The supervisor enforces protocol and diagnostic limits and a finite process task
deadline. It destroys a hung CLI process; the container runtime then force-removes
the named container and requires successful removal before returning any response.
Killing the CLI alone is insufficient. Unconfirmed removal quarantines the domain
and retains slot/control metadata for host reconciliation. A known container name
is the reconciliation key; do not continue with a new domain over unreconciled
resources. In-flight calls own cleanup after `close` stops new admissions.

The task deadline currently covers attached worker execution. Docker control
operations each have a separate finite control timeout. Report these separately;
there is no single hard transport-to-publication timeout. The host must also
provide source and destination I/O timeouts.

## Resource accounting and qualification

Multiply memory, scratch, PID and CPU ceilings by concurrent slots when sizing
the host. Worker input capture and parser scratch must both fit the configured
tmpfs; leave explicit headroom. Host stage reservations remain charged separately.
Container image layers, daemon metadata, logs and control operations also need
host operational limits. Crash reconciliation and volume headroom remain release
gates. A compromised parser may still give incorrect findings.

The Linux CI job builds a test-fixture image, pins the selected base by its
resolved digest, runs the effective restriction probe, sends digest-bound bytes,
and verifies timeout cleanup. The fixture only returns test-only evidence and is
not a production format worker. Local macOS runs intentionally skip these two
container tests. Native configuration/protocol/process tests still run there.
Before release, pin and inventory an approved production base/image, execute the
adversarial corpus and obtain independent containment review. The smoke test does
not replace those gates.
