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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.filegate.formats.office.internal.ManifestXml;
import dev.filegate.formats.office.internal.PackageTargets;
import dev.filegate.formats.office.internal.XmlPartVisitor;

/** Namespace-aware OPC manifest validation. Format semantics must still be checked. */
public final class OfficeManifestReader {
	private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
	private static final String REL_TYPE = "application/vnd.openxmlformats-package.relationships+xml";
	private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
	private final OfficePackageLimits limits;
	private final OfficeLinkPolicy links;

	public OfficeManifestReader(OfficePackageLimits limits, OfficeLinkPolicy links) {
		this.limits = Objects.requireNonNull(limits, "limits");
		this.links = Objects.requireNonNull(links, "links");
	}

	public OfficePackageManifest read(Path captured) throws IOException {
		return read(captured, (part, element, attributes) -> { });
	}

	public OfficePackageManifest read(Path captured, OfficeElementObserver observer) throws IOException {
		Objects.requireNonNull(observer, "observer");
		var defaults = new HashMap<String, String>();
		var overrides = new HashMap<String, String>();
		var declarations = new HashMap<String, List<Map<String, String>>>();
		var inventory = new OfficePackageReader(limits).read(captured, part -> {
			var manifest = visitor(part, defaults, overrides, declarations);
			return (reader, event) -> {
				manifest.visit(reader, event);
				if (event == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
					var attributes = new HashMap<javax.xml.namespace.QName, String>();
					for (int index = 0; index < reader.getAttributeCount(); index++)
						attributes.put(reader.getAttributeName(index), reader.getAttributeValue(index));
					observer.inspect(part, reader.getName(), Map.copyOf(attributes));
				}
			};
		});
		if (!inventory.parts().containsKey("[Content_Types].xml") || !declarations.containsKey("_rels/.rels"))
			throw new IOException("Required package declarations are missing");
		var names = new HashMap<String, String>();
		inventory.parts().keySet().forEach(name -> names.put(name.toLowerCase(Locale.ROOT), name));
		var types = contentTypes(inventory, defaults, overrides, names);
		var relationships = new HashMap<String, List<OfficePackageManifest.Relationship>>();
		for (var part : declarations.entrySet()) {
			if (!REL_TYPE.equals(types.get(part.getKey())))
				throw new IOException("Relationship part has the wrong content type");
			String source = PackageTargets.source(part.getKey());
			if (!source.isEmpty()) {
				source = names.get(source.toLowerCase(Locale.ROOT));
				if (source == null)
					throw new IOException("Relationship source does not exist");
			}
			var resolved = new ArrayList<OfficePackageManifest.Relationship>();
			var ids = new java.util.HashSet<String>();
			for (var attributes : part.getValue()) {
				String id = ManifestXml.require(attributes, "Id");
				if (!id.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}") || !ids.add(id))
					throw new IOException("Duplicate or unsupported relationship identifier");
				String type = ManifestXml.require(attributes, "Type");
				var typeUri = PackageTargets.uri(type);
				if (!typeUri.isAbsolute() || typeUri.getRawFragment() != null)
					throw new IOException("Relationship type must be an absolute URI without a fragment");
				var target = PackageTargets.uri(ManifestXml.require(attributes, "Target"));
				String mode = attributes.getOrDefault("TargetMode", "Internal");
				if (mode.equals("External")) {
					if (!type.equals(REL + "hyperlink"))
						throw new IOException("Automatic external resource relationships are forbidden");
					links.validate(target);
					resolved.add(new OfficePackageManifest.Relationship(id, type, target, true));
				} else if (mode.equals("Internal")) {
					String actual = PackageTargets.resolve(source, target, names);
					resolved.add(new OfficePackageManifest.Relationship(id, type, java.net.URI.create("/" + actual), false));
				} else {
					throw new IOException("Unknown relationship target mode");
				}
			}
			if (relationships.putIfAbsent(source, List.copyOf(resolved)) != null)
				throw new IOException("Duplicate relationship source");
		}
		var main = relationships.getOrDefault("", List.of()).stream()
				.filter(link -> link.type().equals(REL + "officeDocument") && !link.external()).toList();
		if (main.size() != 1)
			throw new IOException("Expected exactly one main Office document");
		String mainPart = main.getFirst().target().getPath().substring(1);
		String type = types.get(mainPart);
		OfficePackageManifest.Kind kind;
		if (DOCX.equals(type))
			kind = OfficePackageManifest.Kind.DOCX;
		else if (XLSX.equals(type))
			kind = OfficePackageManifest.Kind.XLSX;
		else
			throw new IOException("Unsupported main Office content type");
		var root = inventory.parts().get(mainPart);
		String namespace = kind == OfficePackageManifest.Kind.DOCX ? "wordprocessingml" : "spreadsheetml";
		String element = kind == OfficePackageManifest.Kind.DOCX ? "document" : "workbook";
		if (!root.rootName().equals(element)
				|| !root.rootNamespace().equals("http://schemas.openxmlformats.org/" + namespace + "/2006/main"))
			throw new IOException("Main document namespace or root does not match its declared type");
		return new OfficePackageManifest(kind, mainPart, inventory, types, relationships);
	}

	private static XmlPartVisitor visitor(String part, Map<String, String> defaults, Map<String, String> overrides,
			Map<String, List<Map<String, String>>> declarations) {
		if (part.equals("[Content_Types].xml"))
			return new ManifestXml("http://schemas.openxmlformats.org/package/2006/content-types", "Types",
					Map.of("Default", Set.of("Extension", "ContentType"), "Override", Set.of("PartName", "ContentType")),
					(element, attributes) -> {
						String type = ManifestXml.require(attributes, "ContentType").toLowerCase(Locale.ROOT);
						if (type.length() > 256 || !type.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))
							throw new IOException("Unsupported part content type");
						boolean byExtension = element.equals("Default");
						String key = ManifestXml.require(attributes, byExtension ? "Extension" : "PartName");
						if (byExtension && !key.matches("[A-Za-z0-9]+"))
							throw new IOException("Invalid default extension");
						if ((byExtension ? defaults : overrides).putIfAbsent(key.toLowerCase(Locale.ROOT), type) != null)
							throw new IOException("Duplicate content type declaration");
					});
		if (part.toLowerCase(Locale.ROOT).endsWith(".rels")) {
			var values = new ArrayList<Map<String, String>>();
			declarations.put(part, values);
			return new ManifestXml("http://schemas.openxmlformats.org/package/2006/relationships", "Relationships",
					Map.of("Relationship", Set.of("Id", "Type", "Target", "TargetMode")),
					(element, attributes) -> values.add(attributes));
		}
		return (reader, event) -> { };
	}

	private static Map<String, String> contentTypes(OfficePackageInventory inventory, Map<String, String> defaults,
			Map<String, String> overrides, Map<String, String> names) throws IOException {
		for (String part : overrides.keySet()) {
			if (!part.startsWith("/") || !names.containsKey(part.substring(1)))
				throw new IOException("Override targets a missing or noncanonical part");
		}
		var types = new HashMap<String, String>();
		for (String part : inventory.parts().keySet()) {
			if (part.equals("[Content_Types].xml"))
				continue;
			String extension = part.substring(part.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
			String type = overrides.getOrDefault("/" + part.toLowerCase(Locale.ROOT), defaults.get(extension));
			if (type == null)
				throw new IOException("Part content type is not declared");
			if (REL_TYPE.equals(type) != part.toLowerCase(Locale.ROOT).endsWith(".rels"))
				throw new IOException("Relationship content types and part names disagree");
			types.put(part, type);
		}
		return Map.copyOf(types);
	}
}
