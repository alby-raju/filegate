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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public final class PackageTargets {
	private PackageTargets() {
	}

	public static URI uri(String target) throws IOException {
		try {
			return new URI(target);
		} catch (URISyntaxException invalid) {
			throw new IOException("Invalid package relationship URI", invalid);
		}
	}

	public static String resolve(String source, URI target, Map<String, String> canonicalNames) throws IOException {
		if (target.isAbsolute() || target.getRawAuthority() != null || target.getRawQuery() != null
				|| target.getRawFragment() != null || target.getRawPath() == null || target.getRawPath().isEmpty()
				|| target.getRawPath().contains("%") || target.getRawPath().contains("\\"))
			throw new IOException("Unsupported internal package target");
		String resolved = URI.create("/" + source).resolve(target).normalize().getRawPath();
		if (!resolved.startsWith("/") || resolved.startsWith("/../") || resolved.equals("/.."))
			throw new IOException("Package target escapes its root");
		String actual = canonicalNames.get(resolved.substring(1).toLowerCase(java.util.Locale.ROOT));
		if (actual == null)
			throw new IOException("Package relationship target does not exist");
		return actual;
	}

	public static String source(String relationshipsPart) throws IOException {
		if (relationshipsPart.equals("_rels/.rels"))
			return "";
		int marker = relationshipsPart.lastIndexOf("/_rels/");
		String prefix;
		String leaf;
		if (marker >= 0) {
			prefix = relationshipsPart.substring(0, marker + 1);
			leaf = relationshipsPart.substring(marker + 7);
		} else if (relationshipsPart.startsWith("_rels/")) {
			prefix = "";
			leaf = relationshipsPart.substring(6);
		} else {
			throw new IOException("Relationship part has no valid source path");
		}
		if (leaf.length() > 5 && !leaf.contains("/") && leaf.toLowerCase(java.util.Locale.ROOT).endsWith(".rels"))
			return prefix + leaf.substring(0, leaf.length() - 5);
		throw new IOException("Relationship part has no valid source path");
	}
}
