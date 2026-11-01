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
package dev.filegate.formats.spreadsheets.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import dev.filegate.formats.spreadsheets.SpreadsheetViolation;

/** Closed flat-text core metadata; no extension markup or nested payloads are interpreted. */
final class CoreProperties {
	private static final String CORE = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";
	private static final String DC = "http://purl.org/dc/elements/1.1/";
	private static final String TERMS = "http://purl.org/dc/terms/";
	private static final Map<String, Set<String>> FIELDS = Map.of(
			CORE, Set.of("category", "contentStatus", "contentType", "keywords", "lastModifiedBy", "lastPrinted", "revision", "version"),
			DC, Set.of("creator", "description", "identifier", "language", "subject", "title"), TERMS, Set.of("created", "modified"));

	private CoreProperties() { }

	static void verify(InputStream input) throws IOException {
		try {
			var factory = DocumentBuilderFactory.newDefaultInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			var builder = factory.newDocumentBuilder();
			builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External XML is forbidden"); });
			Element root = builder.parse(input).getDocumentElement();
			if (!CORE.equals(root.getNamespaceURI()) || !root.getLocalName().equals("coreProperties")) throw invalid();
			attributes(root, false);
			var seen = new HashSet<QName>();
			for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child instanceof Element property) {
					String namespace = property.getNamespaceURI();
					if (!FIELDS.getOrDefault(namespace, Set.of()).contains(property.getLocalName())
							|| !seen.add(new QName(namespace, property.getLocalName()))) throw invalid();
					attributes(property, true);
					for (Node value = property.getFirstChild(); value != null; value = value.getNextSibling())
						if (value.getNodeType() != Node.TEXT_NODE && value.getNodeType() != Node.CDATA_SECTION_NODE
								&& value.getNodeType() != Node.COMMENT_NODE) throw invalid();
				} else if (child.getNodeType() != Node.COMMENT_NODE
						&& (child.getNodeType() != Node.TEXT_NODE || !child.getNodeValue().isBlank())) throw invalid();
			}
		} catch (ParserConfigurationException | SAXException invalid) {
			throw new SpreadsheetViolation("Core metadata XML cannot be validated");
		}
	}

	private static void attributes(Element element, boolean property) throws SpreadsheetViolation {
		for (int index = 0; index < element.getAttributes().getLength(); index++) {
			Node attribute = element.getAttributes().item(index);
			String namespace = attribute.getNamespaceURI();
			if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)) continue;
			if (property && XMLConstants.XML_NS_URI.equals(namespace) && attribute.getLocalName().equals("lang")
					&& DC.equals(element.getNamespaceURI())) continue;
			if (property && XMLConstants.XML_NS_URI.equals(namespace) && attribute.getLocalName().equals("space")
					&& Set.of("default", "preserve").contains(attribute.getNodeValue())) continue;
			if (property && XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(namespace)
					&& attribute.getLocalName().equals("type") && TERMS.equals(element.getNamespaceURI())) {
				String[] type = attribute.getNodeValue().split(":", -1);
				if (type.length == 2 && type[1].equals("W3CDTF") && TERMS.equals(element.lookupNamespaceURI(type[0]))) continue;
			}
			throw invalid();
		}
	}

	private static SpreadsheetViolation invalid() {
		return new SpreadsheetViolation("Unsupported, duplicate or nested core metadata");
	}
}
