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

package dev.filegate.core.internal.resource;

import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.resource.ResourceUsage;

import java.util.Optional;

public final class ResourceLedger {
    private final ResourceLimits limits;
    private int operations;
    private long bytes;

    public ResourceLedger(ResourceLimits limits) { this.limits = limits; }

    public synchronized Optional<Reservation> reserve(long requested) {
        if (requested <= 0) {
            throw new IllegalArgumentException("Reservation must be positive");
        }
        // Subtraction keeps the admission calculation safe even near Long.MAX_VALUE.
        if (operations >= limits.maxOperations() || requested > limits.maxStagingBytes() - bytes) {
            return Optional.empty();
        }
        operations++;
        bytes += requested;
        return Optional.of(new Reservation(requested));
    }

    public synchronized ResourceUsage usage(int pendingDeletions) {
        return new ResourceUsage(operations, bytes, pendingDeletions);
    }

    public final class Reservation implements AutoCloseable {
        private final long reserved;
        private boolean released;

        private Reservation(long reserved) { this.reserved = reserved; }

        @Override public void close() {
            synchronized (ResourceLedger.this) {
                if (!released) {
                    released = true;
                    operations--;
                    bytes -= reserved;
                }
            }
        }
    }
}
