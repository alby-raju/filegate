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

package dev.filegate.core.internal.inspection;

import dev.filegate.core.content.ApprovedContent;
import dev.filegate.core.inspection.Approved;
import dev.filegate.core.inspection.InspectionReport;
import dev.filegate.core.internal.capture.CapturedArtifact;
import dev.filegate.core.internal.content.VerifiedContent;

public final class ApprovedInspection implements Approved {
    private final InspectionReport report;
    private final VerifiedContent content;

    public ApprovedInspection(InspectionReport report, CapturedArtifact artifact, int bufferBytes) {
        this.report = report;
        this.content = new VerifiedContent(artifact, report.policy(), bufferBytes);
    }

    @Override
    public InspectionReport report() {
        return report;
    }

    public ApprovedContent content() {
        return content;
    }

    @Override
    public void close() {
        content.close();
    }
}
