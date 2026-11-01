#!/usr/bin/env python3
# FileGate
# Policy-based attachment inspection and verified release.
#
# Copyright 2026 alby-raju
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Check built artifact notices and the aggregate Maven inventory."""

import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def main():
    parent = ET.parse(ROOT / "pom.xml").getroot()
    version = parent.findtext("m:version", namespaces=NS)
    group = parent.findtext("m:groupId", namespaces=NS)
    bom = json.loads((ROOT / "target/bom.json").read_text())
    if bom.get("bomFormat") != "CycloneDX" or bom.get("specVersion") != "1.6":
        raise ValueError("Expected a CycloneDX 1.6 inventory")
    components = {(c.get("group"), c["name"], c["version"]) for c in bom["components"]}
    modules = parent.findall("m:modules/m:module", NS)
    for module in modules:
        directory = ROOT / module.text
        pom = ET.parse(directory / "pom.xml").getroot()
        artifact = pom.findtext("m:artifactId", namespaces=NS)
        if (group, artifact, version) not in components:
            raise ValueError(f"Module absent from SBOM: {artifact}")
        jar = directory / "target" / f"{artifact}-{version}.jar"
        with zipfile.ZipFile(jar) as archive:
            for notice in ("LICENSE", "NOTICE"):
                if archive.read(f"META-INF/{notice}") != (ROOT / notice).read_bytes():
                    raise ValueError(f"Incorrect {notice} in {artifact}")
    print(f"Verified inventory membership and packaged notices for {len(modules)} artifacts")


if __name__ == "__main__":
    main()
