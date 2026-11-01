#!/usr/bin/env bash
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

set -euo pipefail

# Retry only transient artifact downloads. Compilation, formatting and test
# failures return immediately. -U also retries Maven's cached download failures.
log=$(mktemp)
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3; do
    if mvn -B -ntp -U "$@" 2>&1 | tee "$log"; then
        exit 0
    else
        status=$?
    fi
    if [[ $attempt -eq 3 ]] || ! grep -Ei \
        '\[ERROR\].*(Could not transfer artifact|Could not GET|Could not HEAD|transfer failed for).*([Ss]tatus[^0-9]*(429|502|503|504)|Connection reset|Read timed out|Connect timed out|Temporary failure in name resolution)' \
        "$log" >/dev/null; then
        exit "$status"
    fi
    printf 'Transient Maven download failure; retrying (attempt %s of 3).\n' "$((attempt + 1))" >&2
    sleep "$((attempt * 10))"
done
