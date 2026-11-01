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

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Allowed clickable link schemes; no policy authorizes external resource loading. */
public enum OfficeLinkPolicy {
	NONE(Set.of()), HTTPS_ONLY(Set.of("https")), WEB_AND_MAIL(Set.of("https", "http", "mailto"));

	private final Set<String> schemes;

	OfficeLinkPolicy(Set<String> schemes) {
		this.schemes = schemes;
	}

	public void validate(URI target) throws IOException {
		String scheme = target.getScheme();
		if (scheme == null || !schemes.contains(scheme.toLowerCase(Locale.ROOT)) || target.getRawUserInfo() != null)
			throw new IOException("External hyperlink is outside the configured link policy");
		if (!scheme.equalsIgnoreCase("mailto") && (target.isOpaque() || target.getHost() == null))
			throw new IOException("Invalid web hyperlink");
		if (scheme.equalsIgnoreCase("mailto") && target.getRawSchemeSpecificPart().isBlank())
			throw new IOException("Empty mail hyperlink");
	}
}
