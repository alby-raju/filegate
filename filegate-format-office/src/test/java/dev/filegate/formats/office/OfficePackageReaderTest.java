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
package dev.filegate.formats.office;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficePackageReaderTest {
	@TempDir
	Path temp;

	private OfficePackageLimits limits() {
		return new OfficePackageLimits(1024 * 1024, 1024 * 1024, 512 * 1024, 128, 32, 10000, 100000);
	}

	private byte[] archive(Map<String, String> parts, boolean stored) throws Exception {
		var bytes = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(bytes)) {
			for (var part : parts.entrySet()) {
				byte[] data = part.getValue().getBytes(StandardCharsets.UTF_8);
				var entry = new ZipEntry(part.getKey());
				if (stored) {
					var crc = new CRC32();
					crc.update(data);
					entry.setMethod(ZipEntry.STORED);
					entry.setSize(data.length);
					entry.setCompressedSize(data.length);
					entry.setCrc(crc.getValue());
				}
				zip.putNextEntry(entry);
				zip.write(data);
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	private OfficePackageInventory read(byte[] bytes, OfficePackageLimits limits) throws Exception {
		Path file = temp.resolve("captured");
		Files.write(file, bytes);
		return new OfficePackageReader(limits).read(file);
	}

	private OfficePackageInventory read(byte[] bytes) throws Exception {
		return read(bytes, limits());
	}

	private void rejects(byte[] bytes) {
		assertThrows(IOException.class, () -> read(bytes));
	}

	private static int signature(byte[] bytes, int signature) {
		for (int i = 0; i <= bytes.length - 4; i++) {
			if (ByteBuffer.wrap(bytes, i, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() == signature)
				return i;
		}
		throw new AssertionError("Fixture signature missing");
	}

	@Test
	void storedAndDeflatedXmlProduceInventoryWithoutChangingCapturedBytes() throws Exception {
		for (boolean stored : new boolean[] { false, true }) {
			var parts = new LinkedHashMap<String, String>();
			parts.put("[Content_Types].xml", "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'/>");
			parts.put("_rels/.rels", "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'/>");
			parts.put("xl/workbook.xml", "<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>");
			parts.put("xl/worksheets/sheet1.xml", "<worksheet><c><f>SUM(A1:A5)</f></c></worksheet>");
			byte[] bytes = archive(parts, stored);
			var inventory = read(bytes);
			assertEquals(4, inventory.parts().size());
			assertEquals("workbook", inventory.parts().get("xl/workbook.xml").rootName());
			assertArrayEquals(bytes, Files.readAllBytes(temp.resolve("captured")));
			assertThrows(UnsupportedOperationException.class, () -> inventory.parts().clear());
		}
	}

	@Test
	void zipViewsTrailingPayloadEncryptionAndCorruptedCrcAreRejected() throws Exception {
		byte[] valid = archive(Map.of("word/document.xml", "<document/>"), true);
		int central = signature(valid, 0x02014b50);
		byte[] mismatch = valid.clone();
		mismatch[central + 46] = 'X';
		rejects(mismatch);
		byte[] encrypted = valid.clone();
		encrypted[6] |= 1;
		encrypted[central + 8] |= 1;
		rejects(encrypted);
		byte[] damaged = valid.clone();
		damaged[30 + "word/document.xml".length() + 2] ^= 1;
		rejects(damaged);
		rejects(Arrays.copyOf(valid, valid.length + 1));
		for (int length = 0; length < valid.length; length++)
			rejects(Arrays.copyOf(valid, length));
	}

	@Test
	void traversalAliasesCaseCollisionsAndNoncanonicalPathsReject() throws Exception {
		for (String name : new String[] { "../document.xml", "/document.xml", "word/../document.xml",
				"word//document.xml", "word/document.xml.", "word/%64ocument.xml", "word/a:b.xml" })
			rejects(archive(Map.of(name, "<document/>"), false));
		var duplicates = new LinkedHashMap<String, String>();
		duplicates.put("word/document.xml", "<document/>");
		duplicates.put("WORD/document.xml", "<document/>");
		rejects(archive(duplicates, false));
	}

	@Test
	void dtdEntitiesProcessingInstructionsAndMalformedXmlReject() throws Exception {
		for (String xml : new String[] {
				"<!DOCTYPE document [<!ENTITY x SYSTEM 'file:///nonexistent-filegate-secret'>]><document>&x;</document>",
				"<!DOCTYPE document [<!ENTITY x 'expanded'>]><document>&x;</document>",
				"<?load href='https://example.invalid'?><document/>",
				"<document><unclosed></document>", "<document/><second/>" })
			rejects(archive(Map.of("word/document.xml", xml), false));
	}

	@Test
	void actualExpansionPartInputAndEntryBudgetsAreIndependent() throws Exception {
		byte[] compressed = archive(Map.of("word/document.xml", "<document>" + "a".repeat(10000) + "</document>"), false);
		assertThrows(IOException.class, () -> read(compressed,
				new OfficePackageLimits(100000, 1000, 1000, 128, 32, 10000, 100000)));
		assertThrows(IOException.class, () -> read(compressed,
				new OfficePackageLimits(1, 100000, 100000, 128, 32, 10000, 100000)));
		byte[] two = archive(Map.of("a.xml", "<a/>", "b.xml", "<b/>"), false);
		assertThrows(IOException.class, () -> read(two,
				new OfficePackageLimits(100000, 100000, 100000, 1, 32, 10000, 100000)));
		assertThrows(IOException.class, () -> read(two,
				new OfficePackageLimits(100000, 7, 7, 128, 32, 10000, 100000)));
	}

	@Test
	void depthEventAndCharacterBudgetsApplyAcrossXmlParts() throws Exception {
		byte[] deep = archive(Map.of("a.xml", "<a>".repeat(40) + "</a>".repeat(40)), false);
		rejects(deep);
		byte[] many = archive(Map.of("a.xml", "<a/>", "b.xml", "<b/>"), false);
		assertThrows(IOException.class, () -> read(many,
				new OfficePackageLimits(100000, 100000, 100000, 128, 32, 4, 100000)));
		assertThrows(IOException.class, () -> read(many,
				new OfficePackageLimits(100000, 100000, 100000, 128, 32, 10000, 1)));
	}
	@Test
	void dishonestExpansionMetadataCannotBypassActualByteLimits() throws Exception {
		byte[] bomb = archive(Map.of("word/document.xml", "<document>" + "a".repeat(10000) + "</document>"), false);
		int central = signature(bomb, 0x02014b50);
		int descriptor = signature(bomb, 0x08074b50);
		ByteBuffer.wrap(bomb).order(ByteOrder.LITTLE_ENDIAN).putInt(central + 24, 1).putInt(descriptor + 12, 1);
		assertThrows(IOException.class, () -> read(bomb,
				new OfficePackageLimits(100000, 1000, 1000, 128, 32, 10000, 100000)));
	}

	@Test
	void binaryPartCrcAndZip64ClaimsAreCheckedWithoutXmlParsing() throws Exception {
		byte[] binary = archive(Map.of("word/media/image.bin", "payload"), true);
		byte[] damaged = binary.clone();
		damaged[30 + "word/media/image.bin".length()] ^= 1;
		rejects(damaged);
		int central = signature(binary, 0x02014b50);
		binary[central + 6] = 45;
		rejects(binary);
	}

}
