/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.modulith.events;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * All uncompleted event publications.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
public interface IncompleteEventPublications {

	/**
	 * Returns all {@link EventPublication}s that have not been completed.
	 *
	 * @return will never be {@literal null}.
	 */
	Collection<? extends EventPublication> findAll();

	/**
	 * Triggers the re-submission of events for which incomplete {@link EventPublication}s are registered. Note, that this
	 * will materialize <em>all</em> incomplete event publications.
	 *
	 * @param filter a {@link Predicate} to select the event publications for which to resubmit events.
	 */
	void resubmitIncompletePublications(Predicate<EventPublication> filter);

	/**
	 * Triggers the re-submission of events for which incomplete {@link EventPublication}s are registered that exceed a
	 * certain age regarding their original publication date.
	 *
	 * @param duration must not be {@literal null}.
	 */
	void resubmitIncompletePublicationsOlderThan(Duration duration);

	/**
	 * Triggers the re-submission of incomplete {@link EventPublication}s matching the given {@link ResubmissionOptions}.
	 *
	 * @param options must not be {@literal null}.
	 * @since 2.0
	 */
	void resubmitIncompletePublications(ResubmissionOptions options);
}
