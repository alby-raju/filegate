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

/** Framework-independent FileGate contracts; implementation packages are deliberately not exported. */
module dev.filegate.core {
    exports dev.filegate.core;
    exports dev.filegate.core.policy;
    exports dev.filegate.core.inspection;
    exports dev.filegate.core.spi;
    exports dev.filegate.core.content;
    exports dev.filegate.core.resource;
    exports dev.filegate.core.preflight;
}
