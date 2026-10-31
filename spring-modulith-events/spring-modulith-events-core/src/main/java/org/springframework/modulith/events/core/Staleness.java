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
	 * The {@link Duration} to be used to indicate that no staleness has been configured for a particular {@link Status},
	 * i.e. that publications in that state are not supposed to be checked for staleness.
	 *
	 * @since 2.0.9, 2.1.2, 2.2
	 */
	Duration UNCONFIGURED_DURATION = Duration.ZERO;

	/**
	 * Returns whether {@link org.springframework.modulith.events.EventPublication}s with the given {@link Status} are
	 * supposed to be checked for staleness at all. Callers are expected to inspect this before calling
	 * {@link #getStaleness(Status)}. Returns {@literal false}, and does not fail, for {@link Status}es that cannot become
	 * stale.
	 *
	 * @param status must not be {@literal null}.
	 * @return whether staleness needs to be handled for the given {@link Status}.
	 * @since 2.0.9, 2.1.2, 2.2
	 */
	default boolean isMonitored(Status status) {
		return isStaleable(status) && !UNCONFIGURED_DURATION.equals(getStaleness(status));
	}

	/**
	 * Returns whether {@link org.springframework.modulith.events.EventPublication}s with the given {@link Status} can
	 * become stale at all.
	 *
	 * @param status must not be {@literal null}.
	 * @return whether the given {@link Status} can become stale.
	 * @since 2.0.9, 2.1.2, 2.2
	 */
	default boolean isStaleable(Status status) {

		return switch (status) {
			case PUBLISHED, PROCESSING, RESUBMITTED -> true;
			default -> false;
		};
	}

	/**
	 * Returns the {@link Duration} after which {@link org.springframework.modulith.events.EventPublication}s with a
	 * certain {@link Status} are considered stale. Guaranteed to be positive if {@link #isMonitored(Status)} returned
	 * {@literal true} before. Rejects {@link Status}es not considered {@link #isStaleable(Status) staleable}.
	 *
	 * @param status must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @throws IllegalArgumentException in case the given {@link Status} cannot become stale.
	 * @see #isMonitored(Status)
	 * @see #isStaleable(Status)
	 */
	Duration getStaleness(Status status);
}
