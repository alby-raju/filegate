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
package dev.filegate.worker.internal.process;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** Monotonic deadline shared by all phases of one attached task. */
public final class TaskDeadline {
	private final long started = System.nanoTime();
	private final long timeoutNanos;

	public TaskDeadline(Duration timeout) {
		this.timeoutNanos = timeout.toNanos();
	}

	public long remainingNanos() throws TimeoutException {
		long remaining = timeoutNanos - (System.nanoTime() - started);
		if (remaining <= 0)
			throw new TimeoutException();
		return remaining;
	}
}
