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
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import dev.filegate.formats.office.OfficePackageLimits;

/** One budget per package; no shared factories or resolver state across inspections. */
public final class XmlEnvelope {
	public record Root(String namespace, String name) {
	}

	private final OfficePackageLimits limits;
	private final XMLInputFactory factory;
	private long events;
	private long characters;

	public XmlEnvelope(OfficePackageLimits limits) {
		this.limits = limits;
		factory = XMLInputFactory.newDefaultFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setXMLResolver((publicId, systemId, base, namespace) -> {
			throw new XMLStreamException("External XML resources are forbidden");
		});
	}

	public Root inspect(InputStream input) throws IOException {
		try {
			var reader = factory.createXMLStreamReader(input);
			try {
				Root root = null;
				int depth = 0;
				while (reader.hasNext()) {
					int event = reader.next();
					if (++events > limits.maxXmlEvents())
						throw invalid();
					switch (event) {
					case XMLStreamConstants.START_ELEMENT -> {
						if (++depth > limits.maxXmlDepth() || reader.getAttributeCount() > 256
								|| reader.getNamespaceCount() > 64)
							throw invalid();
						if (root == null)
							root = new Root(value(reader.getNamespaceURI()), reader.getLocalName());
						charge(reader.getLocalName().length() + value(reader.getNamespaceURI()).length());
						for (int i = 0; i < reader.getAttributeCount(); i++)
							charge((long) reader.getAttributeLocalName(i).length() + reader.getAttributeValue(i).length()
									+ value(reader.getAttributeNamespace(i)).length());
						for (int i = 0; i < reader.getNamespaceCount(); i++)
							charge((long) value(reader.getNamespacePrefix(i)).length() + value(reader.getNamespaceURI(i)).length());
					}
					case XMLStreamConstants.END_ELEMENT -> depth--;
					case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
							XMLStreamConstants.SPACE, XMLStreamConstants.COMMENT -> charge(reader.getTextLength());
					case XMLStreamConstants.DTD, XMLStreamConstants.ENTITY_REFERENCE,
							XMLStreamConstants.PROCESSING_INSTRUCTION -> throw invalid();
					default -> { }
					}
				}
				if (root == null || depth != 0)
					throw invalid();
				return root;
			} finally {
				reader.close();
			}
		} catch (XMLStreamException malformed) {
			throw new IOException("Malformed or unsupported Office XML", malformed);
		}
	}

	private void charge(long amount) throws IOException {
		if (amount > limits.maxXmlCharacters() - characters)
			throw invalid();
		characters += amount;
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}

	private static IOException invalid() {
		return new IOException("Unsupported XML construct or exhausted XML envelope");
	}
}
