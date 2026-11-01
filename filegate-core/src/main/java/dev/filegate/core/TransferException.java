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

package dev.filegate.core;

import java.io.IOException;

/** A failed copy grants no receipt. The host must abort its private destination. */
public final class TransferException extends IOException {
    private static final long serialVersionUID = 1L;
    private final TransferFailure failure;

    TransferException(TransferFailure failure) {
        // Do not leak content paths or downstream exception messages.
        super(failure.name());
        this.failure = failure;
    }

    public TransferFailure failure() { return failure; }
}
