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
package dev.filegate.worker.process;

import java.time.Duration;

public record WorkerLimits(int maxTasks, Duration taskTimeout, Duration terminationGrace, Duration failureBackoff,
		int maxStderrBytes) {
	public WorkerLimits {
		if (maxTasks < 1 || maxTasks > 128 || taskTimeout.isNegative() || taskTimeout.isZero()
				|| taskTimeout.compareTo(Duration.ofMinutes(5)) > 0 || terminationGrace.isNegative()
				|| terminationGrace.isZero() || terminationGrace.compareTo(Duration.ofSeconds(5)) > 0
				|| failureBackoff.isNegative() || failureBackoff.compareTo(Duration.ofMinutes(1)) > 0
				|| maxStderrBytes < 0 || maxStderrBytes > 1024 * 1024) {
			throw new IllegalArgumentException("Invalid worker resource envelope");
		}
	}
}
