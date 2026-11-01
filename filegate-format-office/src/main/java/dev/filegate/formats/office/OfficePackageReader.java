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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipInputStream;

import dev.filegate.formats.office.internal.XmlEnvelope;
import dev.filegate.formats.office.internal.ZipEnvelope;

/**
 * Worker-only bounded ZIP/XML inventory. Does not validate document semantics,
 * approve files, extract paths, resolve relationships, or evaluate formulas.
 */
public final class OfficePackageReader {
	private final OfficePackageLimits limits;

	public OfficePackageReader(OfficePackageLimits limits) {
		this.limits = Objects.requireNonNull(limits, "limits");
	}

	public OfficePackageInventory read(Path captured) throws IOException {
		var expected = ZipEnvelope.read(captured, limits);
		var parts = new HashMap<String, OfficePackageInventory.Part>();
		var xml = new XmlEnvelope(limits);
		var budget = new ExpansionBudget(limits);
		try (var input = new ZipInputStream(Files.newInputStream(captured))) {
			for (var entry : expected) {
				var local = input.getNextEntry();
				if (local == null || !local.getName().equals(entry.name()) || local.getMethod() != entry.method())
					throw new IOException("ZIP entry views disagree");
				var bounded = budget.open(input);
				var root = new XmlEnvelope.Root("", "");
				String name = entry.name().toLowerCase(Locale.ROOT);
				if (name.endsWith(".xml") || name.endsWith(".rels"))
					root = xml.inspect(bounded);
				byte[] buffer = new byte[8192];
				while (bounded.read(buffer) != -1) { /* Verify all bytes and ZIP CRC, including unparsed binary parts. */ }
				if (bounded.count != entry.size() || local.getSize() != entry.size()
						|| local.getCompressedSize() != entry.compressed() || local.getCrc() != entry.crc())
					throw new IOException("ZIP entry size, compressed stream or CRC mismatch");
				if (!name.endsWith("/"))
					parts.put(entry.name(), new OfficePackageInventory.Part(entry.size(), entry.crc(), root.namespace(), root.name()));
			}
			if (input.getNextEntry() != null)
				throw new IOException("Unexpected local ZIP entry");
		}
		return new OfficePackageInventory(parts, budget.total);
	}

	private static final class ExpansionBudget {
		private final OfficePackageLimits limits;
		private long total;

		private ExpansionBudget(OfficePackageLimits limits) {
			this.limits = limits;
		}

		private PartInput open(InputStream input) {
			return new PartInput(input);
		}

		private final class PartInput extends FilterInputStream {
			private long count;

			private PartInput(InputStream input) {
				super(input);
			}

			@Override
			public int read() throws IOException {
				int value = in.read();
				if (value != -1)
					charge(1);
				return value;
			}

			@Override
			public int read(byte[] bytes, int offset, int length) throws IOException {
				int read = in.read(bytes, offset, Math.min(length, 8192));
				if (read > 0)
					charge(read);
				return read;
			}

			private void charge(int bytes) throws IOException {
				if (bytes > limits.maxPartBytes() - count || bytes > limits.maxExpandedBytes() - total)
					throw new IOException("Office package expansion allowance exhausted");
				count += bytes;
				total += bytes;
			}

			@Override
			public void close() { /* XML readers do not own the package stream. */ }
		}
	}
}
