package dev.filegate.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InspectionLifecycleTest {
    @TempDir Path temp;
    @FunctionalInterface interface Body { CheckEvidence inspect(InspectionInput input) throws Exception; }

    static InspectionCheck check(String id, Body body) {
        return new InspectionCheck() {
            public CheckDescriptor descriptor() { return PolicyEvidenceTest.descriptor(id); }
            public CheckEvidence inspect(InspectionInput input) throws Exception { return body.inspect(input); }
        };
    }

    FileGate gate(InspectionCheck... checks) throws IOException {
        return FileGate.open(temp, new ResourceLimits(2, 128, 1024),
                PolicyEvidenceTest.policy(32, List.of(checks).stream().map(c -> c.descriptor().id()).toArray(String[]::new)),
                List.of(checks));
    }

    @Test void onlyAllRequiredEvidenceGrantsContentAndClosingReclaimsStage() throws Exception {
        try (var gate = gate(check("a", input -> {
            assertEquals("../../untrusted", input.claims().filename());
            try (var stream = input.openStream()) { assertArrayEquals(new byte[] {1, 2}, stream.readAllBytes()); }
            return CheckEvidence.pass("a");
        }))) {
            var result = gate.inspect(new ByteArrayInputStream(new byte[] {1, 2}), new InputClaims("../../untrusted", ""));
            var approved = assertInstanceOf(Approved.class, result);
            assertEquals(2, approved.content().identity().length());
            assertEquals(approved.content().identity(), result.report().artifact().orElseThrow());
            assertEquals(32, gate.resourceUsage().reservedStagingBytes());
            result.close();
            result.close();
            assertEquals(0, gate.resourceUsage().reservedStagingBytes());
        }
    }

    @Test void exceptionsAndInvalidEvidenceNeverApproveAndSkippedChecksAreNotRun() throws Exception {
        for (Body body : List.<Body>of(input -> { throw new IOException("secret parser detail"); },
                input -> null, input -> CheckEvidence.pass("foreign"))) {
            try (var gate = gate(check("a", body), check("b", input -> { fail("Must stop after missing evidence"); return null; }));
                 var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
                assertInstanceOf(Blocked.class, result);
                assertEquals(Verdict.INCOMPLETE, result.report().verdict());
                assertEquals(CheckStatus.NOT_RUN, result.report().evidence().get(1).status());
                assertEquals(0, gate.resourceUsage().reservedStagingBytes());
            }
        }
    }

    @Test void rejectionStopsExpensiveLaterChecks() throws Exception {
        try (var gate = gate(check("a", input -> new CheckEvidence("a", CheckStatus.FAIL, ReasonCode.POLICY_VIOLATION)),
                check("b", input -> { fail("Early denial must skip later checks"); return null; }));
             var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
            assertEquals(Verdict.REJECTED, result.report().verdict());
            assertInstanceOf(Blocked.class, result);
        }
    }

    @Test void inspectorAccessIsRevokedAfterInvocation() throws Exception {
        var retained = new AtomicReference<InspectionInput>();
        var stream = new AtomicReference<InputStream>();
        try (var gate = gate(check("a", input -> {
            retained.set(input);
            stream.set(input.openStream());
            assertThrows(IllegalStateException.class, input::openStream);
            return CheckEvidence.pass("a");
        })); var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
            assertEquals(Verdict.APPROVED, result.report().verdict());
            assertThrows(IllegalStateException.class, () -> retained.get().openStream());
            assertThrows(IOException.class, () -> stream.get().read());
        }
    }

    @Test void activeLeaseSurvivesOwnerCloseAndNewLeasesAreRefused() throws Exception {
        try (var store = new StageStore(temp, new ResourceLimits(1, 32, 1024))) {
            var artifact = store.capture(new ByteArrayInputStream(new byte[] {7}), 32).artifact();
            var active = artifact.retain();
            artifact.close();
            assertThrows(IllegalStateException.class, artifact::retain);
            assertEquals(32, store.usage().reservedStagingBytes());
            try (var input = active.open()) { assertEquals(7, input.read()); }
            active.close();
            assertEquals(0, store.usage().reservedStagingBytes());
            assertFalse(Files.exists(artifact.path));
        }
    }

    @Test void reloadDoesNotChangeInFlightPolicy() throws Exception {
        var entered = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        try (var gate = gate(check("a", input -> { entered.countDown(); proceed.await(); return CheckEvidence.pass("a"); }));
             var pool = Executors.newSingleThreadExecutor()) {
            var old = PolicyEvidenceTest.policy(32, "a");
            var next = PolicyEvidenceTest.policy(64, "a");
            var running = pool.submit(() -> gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            gate.reload(next);
            proceed.countDown();
            try (var result = running.get()) { assertEquals(old.fingerprint(), result.report().policy().fingerprint()); }
            try (var result = gate.inspect(new ByteArrayInputStream(new byte[1]), InputClaims.NONE)) {
                assertEquals(next.fingerprint(), result.report().policy().fingerprint());
            }
        } finally { proceed.countDown(); }
    }

    @Test void pathInputIsCopiedAndUnavailableSourcesBlock() throws Exception {
        Path source = temp.resolve("source.bin");
        Files.write(source, new byte[] {1});
        try (var gate = gate(check("a", input -> {
            Files.write(source, new byte[] {2});
            try (var stream = input.openStream()) { assertEquals(1, stream.read()); }
            return CheckEvidence.pass("a");
        })); var result = gate.inspect(source, InputClaims.NONE)) {
            assertEquals(Verdict.APPROVED, result.report().verdict());
            assertArrayEquals(new byte[] {2}, Files.readAllBytes(source));
            try (var missing = gate.inspect(temp.resolve("missing"), InputClaims.NONE)) {
                assertEquals(Verdict.INCOMPLETE, missing.report().verdict());
            }
        }
    }

    @Test void isolatedPoliciesCannotFallBackToEmbeddedAndBudgetsMustFit() {
        var check = check("a", input -> CheckEvidence.pass("a"));
        var isolated = CompiledPolicy.compile(new PolicySpec("p", Use.DOWNLOAD, 32, ExecutionMode.ISOLATED,
                List.of("a")), List.of(check.descriptor()));
        assertThrows(IllegalArgumentException.class,
                () -> FileGate.open(temp, new ResourceLimits(1, 32, 1024), isolated, List.of(check)));
        assertThrows(IllegalArgumentException.class,
                () -> FileGate.open(temp, new ResourceLimits(1, 16, 1024), PolicyEvidenceTest.policy(32, "a"), List.of(check)));
    }
}
