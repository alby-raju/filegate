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
package dev.filegate.formats.office.internal;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import dev.filegate.formats.office.OfficePackageLimits;

/** Strict classic ZIP envelope: one contiguous local view and one matching central view. */
public final class ZipEnvelope {
	public record Entry(String name, long size, long compressed, long crc, int method, int flags, long offset) {
	}

	private ZipEnvelope() {
	}

	public static List<Entry> read(Path source, OfficePackageLimits limits) throws IOException {
		try (var file = new RandomAccessFile(source.toFile(), "r")) {
			long length = file.length();
			if (length < 22 || length > limits.maxInputBytes())
				throw invalid();
			// Comments, multi-disk and ZIP64 archives are outside this initial envelope.
			file.seek(length - 22);
			if (u32(file) != 0x06054b50L || u16(file) != 0 || u16(file) != 0)
				throw invalid();
			int count = u16(file);
			if (count == 0 || count == 0xffff || count > limits.maxEntries() || u16(file) != count)
				throw invalid();
			long centralBytes = u32(file);
			long central = u32(file);
			if (u16(file) != 0 || centralBytes > length - 22 || central != length - 22 - centralBytes)
				throw invalid();
			file.seek(central);
			var entries = new ArrayList<Entry>(count);
			var names = new HashSet<String>();
			long expanded = 0;
			for (int index = 0; index < count; index++) {
				if (file.getFilePointer() > length - 22 - 46 || u32(file) != 0x02014b50L)
					throw invalid();
				u16(file); // Creator platform is not used to interpret paths.
				if (u16(file) > 20)
					throw invalid();
				int flags = u16(file);
				int method = u16(file);
				if ((flags & ~0x080e) != 0 || (method != 0 && method != 8) || (method == 0 && (flags & 14) != 0))
					throw invalid();
				u32(file); // DOS date/time.
				long crc = u32(file);
				long compressed = u32(file);
				long size = u32(file);
				int nameLength = u16(file);
				int extraLength = u16(file);
				int commentLength = u16(file);
				if (u16(file) != 0)
					throw invalid();
				u16(file);
				long attributes = u32(file);
				int kind = (int) ((attributes >>> 16) & 0170000);
				long offset = u32(file);
				if (nameLength == 0 || nameLength > 512 || commentLength != 0
						|| (kind != 0 && kind != 0100000 && kind != 0040000)
						|| size == 0xffffffffL || compressed == 0xffffffffL || offset >= central
						|| size > limits.maxPartBytes() || size > limits.maxExpandedBytes() - expanded
						|| nameLength + extraLength > length - 22 - file.getFilePointer())
					throw invalid();
				byte[] nameBytes = new byte[nameLength];
				file.readFully(nameBytes);
				String name = new String(nameBytes, StandardCharsets.US_ASCII);
				validateName(name);
				if (!Arrays.equals(nameBytes, name.getBytes(StandardCharsets.US_ASCII))
						|| !names.add(name.toLowerCase(Locale.ROOT)) || (name.endsWith("/") && size != 0))
					throw invalid();
				readExtra(file, extraLength);
				expanded += size;
				entries.add(new Entry(name, size, compressed, crc, method, flags, offset));
			}
			if (file.getFilePointer() != length - 22)
				throw invalid();
			entries.sort(Comparator.comparingLong(Entry::offset));
			long next = 0;
			for (int index = 0; index < entries.size(); index++) {
				Entry entry = entries.get(index);
				if (entry.offset() != next)
					throw invalid();
				file.seek(next);
				long end = index + 1 < entries.size() ? entries.get(index + 1).offset() : central;
				next = readLocal(file, entry, end);
			}
			if (next != central)
				throw invalid();
			return List.copyOf(entries);
		}
	}

	private static long readLocal(RandomAccessFile file, Entry entry, long end) throws IOException {
		if (end - entry.offset() < 30 || u32(file) != 0x04034b50L || u16(file) > 20
				|| u16(file) != entry.flags() || u16(file) != entry.method())
			throw invalid();
		u32(file);
		long crc = u32(file);
		long compressed = u32(file);
		long size = u32(file);
		int nameLength = u16(file);
		int extraLength = u16(file);
		if (nameLength != entry.name().length() || nameLength + extraLength > end - file.getFilePointer())
			throw invalid();
		byte[] name = new byte[nameLength];
		file.readFully(name);
		if (!Arrays.equals(name, entry.name().getBytes(StandardCharsets.US_ASCII)))
			throw invalid();
		readExtra(file, extraLength);
		if (entry.compressed() > end - file.getFilePointer())
			throw invalid();
		long dataEnd = file.getFilePointer() + entry.compressed();
		boolean descriptor = (entry.flags() & 8) != 0;
		if (!descriptor) {
			if (crc != entry.crc() || compressed != entry.compressed() || size != entry.size() || dataEnd != end)
				throw invalid();
		} else {
			if ((crc != 0 && crc != entry.crc()) || (compressed != 0 && compressed != entry.compressed())
					|| (size != 0 && size != entry.size()) || (end - dataEnd != 12 && end - dataEnd != 16))
				throw invalid();
			file.seek(dataEnd);
			if (end - dataEnd == 16 && u32(file) != 0x08074b50L)
				throw invalid();
			if (u32(file) != entry.crc() || u32(file) != entry.compressed() || u32(file) != entry.size())
				throw invalid();
		}
		return end;
	}

	private static void validateName(String name) throws IOException {
		if (name.startsWith("/") || !name.matches("[A-Za-z0-9_./\\[\\]-]+"))
			throw invalid();
		String path = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		for (String segment : path.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith("."))
				throw invalid();
		}
	}

	private static void readExtra(RandomAccessFile file, int length) throws IOException {
		long end = file.getFilePointer() + length;
		while (file.getFilePointer() < end) {
			if (end - file.getFilePointer() < 4)
				throw invalid();
			int id = u16(file);
			int size = u16(file);
			if (size > end - file.getFilePointer() || id == 1 || id == 0x7075 || id == 0x9901)
				throw invalid();
			file.seek(file.getFilePointer() + size);
		}
	}

	private static int u16(RandomAccessFile file) throws IOException {
		return file.readUnsignedByte() | (file.readUnsignedByte() << 8);
	}

	private static long u32(RandomAccessFile file) throws IOException {
		return Integer.toUnsignedLong(u16(file) | (u16(file) << 16));
	}

	private static IOException invalid() {
		return new IOException("Unsupported or inconsistent Office ZIP envelope");
	}
}
