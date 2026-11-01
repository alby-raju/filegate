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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/** Package manifests have a root and flat, attribute-only child declarations. */
public final class ManifestXml implements XmlPartVisitor {
	@FunctionalInterface
	public interface Declaration {
		void accept(String element, Map<String, String> attributes) throws IOException;
	}

	private final String namespace;
	private final String root;
	private final Map<String, Set<String>> children;
	private final Declaration declaration;
	private int depth;

	public ManifestXml(String namespace, String root, Map<String, Set<String>> children, Declaration declaration) {
		this.namespace = namespace;
		this.root = root;
		this.children = children;
		this.declaration = declaration;
	}

	@Override
	public void visit(XMLStreamReader reader, int event) throws IOException {
		if (event == XMLStreamConstants.START_ELEMENT) {
			depth++;
			String name = reader.getLocalName();
			if (!namespace.equals(reader.getNamespaceURI()) || depth > 2)
				throw new IOException("Unsupported package manifest structure");
			if (depth == 1) {
				if (!name.equals(root) || reader.getAttributeCount() != 0)
					throw new IOException("Invalid package manifest root");
			} else {
				Set<String> allowed = children.get(name);
				if (allowed == null)
					throw new IOException("Unknown package manifest declaration");
				var attributes = new HashMap<String, String>();
				for (int i = 0; i < reader.getAttributeCount(); i++) {
					String attribute = reader.getAttributeLocalName(i);
					String uri = reader.getAttributeNamespace(i);
					if ((uri != null && !uri.isEmpty()) || !allowed.contains(attribute))
						throw new IOException("Unknown package manifest attribute");
					attributes.put(attribute, reader.getAttributeValue(i));
				}
				declaration.accept(name, Map.copyOf(attributes));
			}
		} else if (event == XMLStreamConstants.END_ELEMENT) {
			depth--;
		} else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
				&& !reader.isWhiteSpace()) {
			throw new IOException("Unexpected package manifest text");
		}
	}

	public static String require(Map<String, String> attributes, String name) throws IOException {
		String value = attributes.get(name);
		if (value == null || value.isBlank())
			throw new IOException("Missing package manifest attribute");
		return value;
	}
}
