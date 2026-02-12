/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events.core;

import java.time.Duration;

import org.springframework.modulith.events.EventPublication.Status;

/**
 * Allows to look up the {@link Duration} after which {@link org.springframework.modulith.events.EventPublication}s with
 * a certain {@link Status} are considered stale.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
public interface Staleness {

	/**
	 * Returns the {@link Duration} after which {@link org.springframework.modulith.events.EventPublication}s with a
	 * certain {@link Status} are considered stale.
	 *
	 * @param status must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Duration getStaleness(Status status);
}
