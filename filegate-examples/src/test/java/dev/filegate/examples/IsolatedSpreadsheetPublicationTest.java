/*
 * FileGate
 * Policy-based attachment inspection and verified release.
 *
 * Copyright 2026 alby-raju
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.filegate.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import dev.filegate.core.FileGate;
import dev.filegate.core.inspection.Approved;
import dev.filegate.core.inspection.Blocked;
import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.Verdict;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.policy.ExecutionMode;
import dev.filegate.core.policy.PolicySpec;
import dev.filegate.core.policy.Use;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.formats.office.OfficeLinkPolicy;
import dev.filegate.formats.office.OfficePackageLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetLimits;
import dev.filegate.formats.spreadsheets.SpreadsheetProfile;
import dev.filegate.spreadsheets.worker.IsolatedSpreadsheetCheck;
import dev.filegate.worker.linux.ContainerSpec;
import dev.filegate.worker.linux.LinuxContainerRuntime;
import dev.filegate.worker.linux.SandboxLimits;
import dev.filegate.worker.process.WorkerLimits;

@EnabledIfEnvironmentVariable(named = "FILEGATE_DOCKER_IMAGE", matches = ".+")
class IsolatedSpreadsheetPublicationTest {
	@TempDir
	Path temp;

	private byte[] spreadsheet(String formula) throws Exception {
		try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
			var sheet = book.createSheet("Sheet1");
			sheet.createRow(0).createCell(0).setCellValue(5);
			sheet.createRow(1).createCell(0).setCellValue(7);
			sheet.getRow(0).createCell(1).setCellFormula(formula);
			book.write(bytes);
			return bytes.toByteArray();
		}
	}

	@Test
	void actualContainerApprovesAndPublishesOnlyTheBoundedBasicXlsxProfile() throws Exception {
		Path storage = Files.createDirectory(temp.resolve("storage"));
		var sandbox = new SandboxLimits(512L * 1024 * 1024, 64L * 1024 * 1024, 128, 1000);
		var profile = new SpreadsheetProfile(new OfficePackageLimits(1024 * 1024, 8 * 1024 * 1024,
				2 * 1024 * 1024, 128, 64, 100000, 2 * 1024 * 1024),
				new SpreadsheetLimits(8, 1000, 10000, 1000, 100, 8192, 4096, 32767), OfficeLinkPolicy.HTTPS_ONLY);
		try (var runtime = LinuxContainerRuntime.open(temp, new ContainerSpec(Path.of("/usr/bin/docker"),
				System.getenv("FILEGATE_DOCKER_IMAGE"), 65534, 65534, sandbox),
				new WorkerLimits(1, Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ZERO, 4096))) {
			var check = new IsolatedSpreadsheetCheck("xlsx.basic", runtime, profile);
			var policy = CompiledPolicy.compile(new PolicySpec("xlsx.basic", Use.DOWNLOAD, 1024 * 1024,
					ExecutionMode.ISOLATED, List.of("xlsx.basic")), List.of(check.descriptor()));
			try (var gate = FileGate.open(temp, new ResourceLimits(1, 2 * 1024 * 1024, 8192), policy, List.of(check))) {
				byte[] valid = spreadsheet("SUM(A1:A2)");
				// Synthetic regression input only; never copy real inspected files into CI diagnostics.
				try (var diagnostic = Files.newOutputStream(Path.of("target/synthetic-spreadsheet-request.bin"))) {
					var identity = new dev.filegate.core.content.ArtifactIdentity(valid.length,
							java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(valid)));
					dev.filegate.worker.protocol.WorkerProtocol.writeRequest(diagnostic,
							new dev.filegate.worker.protocol.ProtocolBinding(java.util.UUID.randomUUID(), identity,
									policy.fingerprint(), "basic-xlsx-worker-v1-poi-6.0.0", List.of("xlsx.basic")));
					diagnostic.write(valid);
				}
				try (var result = gate.inspect(new ByteArrayInputStream(valid), InputClaims.NONE)) {
					var approved = assertInstanceOf(Approved.class, result, () -> result.report().verdict() + " " + result.report().evidence() + " " + result.report().reasons());
					Path stored = PublicationExample.publish(approved.content(), storage);
					assertArrayEquals(valid, Files.readAllBytes(stored));
				}
				try (var result = gate.inspect(new ByteArrayInputStream(spreadsheet("INDIRECT(A1)")), InputClaims.NONE)) {
					assertInstanceOf(Blocked.class, result);
					assertEquals(Verdict.REJECTED, result.report().verdict());
				}
				runtime.close();
				try (var result = gate.inspect(new ByteArrayInputStream(valid), InputClaims.NONE)) {
					assertEquals(Verdict.INCOMPLETE, result.report().verdict());
				}
			}
		}
		try (var files = Files.list(storage)) { assertEquals(1, files.count()); }
	}
}
