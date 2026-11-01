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
package dev.filegate.core.spi;

import dev.filegate.core.inspection.CheckEvidence;
import dev.filegate.core.policy.CheckDescriptor;

/**
 * Explicitly trusted embedded check. Metadata is bound at gate construction.
 * Implementations may serve concurrent files and must be thread-safe. Deep
 * hostile-file parsers require isolated workers.
 */
public interface InspectionCheck {
	CheckDescriptor descriptor();

	/**
	 * Input access is revoked when this call returns; do not retain it or start
	 * background tasks.
	 */
	CheckEvidence inspect(InspectionInput input) throws Exception;
}
