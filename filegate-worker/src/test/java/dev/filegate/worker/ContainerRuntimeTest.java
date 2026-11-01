package dev.filegate.worker;

import dev.filegate.core.ArtifactIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Required in the Linux container CI job; deliberately unavailable on native macOS. */
@EnabledIfEnvironmentVariable(named = "FILEGATE_DOCKER_IMAGE", matches = ".+")
class ContainerRuntimeTest {
    @TempDir Path temp;

    private LinuxContainerRuntime runtime() throws Exception {
        return LinuxContainerRuntime.open(temp,
                new ContainerSpec(Path.of("/usr/bin/docker"), System.getenv("FILEGATE_DOCKER_IMAGE"),
                        65534, 65534, ContainerSpecTest.limits()),
                new WorkerLimits(1, Duration.ofSeconds(8), Duration.ofSeconds(2), Duration.ZERO, 2048));
    }

    private List<String> arguments(String mode) {
        var args = new ArrayList<>(List.of(WorkerStubMain.class.getName(), mode));
        args.addAll(List.of(ContainerSpecTest.limits().arguments()));
        return args;
    }

    private ProtocolBinding binding(byte[] bytes) throws Exception {
        return new ProtocolBinding(UUID.randomUUID(), new ArtifactIdentity(bytes.length,
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))),
                "b".repeat(64), "test-only-sandbox-fixture", List.of("test-only"));
    }

    @Test void kernelProbeAndBoundPayloadPassInAnActuallyRestrictedContainer() throws Exception {
        try (var runtime = runtime()) {
            byte[] bytes = {1, 2, 3};
            var run = runtime.execute(binding(bytes), new ByteArrayInputStream(bytes), arguments("sandbox-pass"));
            assertTrue(run.succeeded(), () -> "Failure: " + run.failure());
        }
        try (var files = Files.list(temp)) { assertEquals(0, files.count()); }
    }

    @Test void timeoutRevokesContainerAndNoLateResultIsAccepted() throws Exception {
        try (var runtime = runtime()) {
            byte[] bytes = {1};
            var run = runtime.execute(binding(bytes), new ByteArrayInputStream(bytes), arguments("sandbox-hang"));
            assertEquals(WorkerFailure.TIMEOUT, run.failure());
        }
        try (var files = Files.list(temp)) { assertEquals(0, files.count()); }
    }
}
