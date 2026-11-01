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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import dev.filegate.core.inspection.InputClaims;
import dev.filegate.core.inspection.Inspection;
import dev.filegate.core.internal.capture.StageStore;
import dev.filegate.core.internal.inspection.CheckRegistry;
import dev.filegate.core.internal.inspection.InspectionEngine;
import dev.filegate.core.policy.CompiledPolicy;
import dev.filegate.core.resource.ResourceLimits;
import dev.filegate.core.resource.ResourceUsage;
import dev.filegate.core.spi.InspectionCheck;

/** One long-lived gate per resource domain. Assembly and lifecycle stay at this boundary. */
public final class FileGate implements AutoCloseable {
    private final CheckRegistry registry;
    private final ResourceLimits limits;
    private final InspectionEngine engine;
    private final AtomicReference<CheckRegistry.Plan> plan;

    private FileGate(CheckRegistry registry, ResourceLimits limits, InspectionEngine engine, CheckRegistry.Plan plan) {
        this.registry = registry;
        this.limits = limits;
        this.engine = engine;
        this.plan = new AtomicReference<>(plan);
    }

    public static FileGate open(Path stagingParent, ResourceLimits limits, CompiledPolicy policy,
                                List<InspectionCheck> installed) throws IOException {
        Objects.requireNonNull(limits, "limits");
        var registry = new CheckRegistry(installed);
        var plan = registry.bind(policy, limits);
        var engine = new InspectionEngine(new StageStore(stagingParent, limits), limits.captureBufferBytes());
        return new FileGate(registry, limits, engine, plan);
    }

    /** Existing operations retain their resolved plan and policy snapshot. */
    public void reload(CompiledPolicy next) { plan.set(registry.bind(next, limits)); }

    public Inspection inspect(InputStream source, InputClaims claims) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(claims, "claims");
        return engine.inspect(plan.get(), source, claims);
    }

    /** Path sources are copied into owned staging; caller files are never modified. */
    public Inspection inspect(Path source, InputClaims claims) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(claims, "claims");
        return engine.inspect(plan.get(), source, claims);
    }

    public ResourceUsage resourceUsage() { return engine.resourceUsage(); }
    public void retryCleanup() { engine.retryCleanup(); }

    /** Stops new capture. Existing results remain caller-owned. */
    @Override public void close() { engine.close(); }
}
