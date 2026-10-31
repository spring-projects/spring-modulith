/*
 * Copyright 2026 the original author or authors.
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

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.function.Predicate;

import org.springframework.modulith.events.AbandonedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link AbandonedEventPublications}.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
public class DefaultAbandonedEventPublications implements AbandonedEventPublications {

	private final EventPublicationRepository events;
	private final Clock clock;

	/**
	 * Creates a new {@link DefaultAbandonedEventPublications} for the given {@link EventPublicationRepository} and
	 * {@link Clock}.
	 *
	 * @param events must not be {@literal null}.
	 * @param clock must not be {@literal null}.
	 */
	public DefaultAbandonedEventPublications(EventPublicationRepository events, Clock clock) {

		Assert.notNull(events, "EventPublicationRepository must not be null!");
		Assert.notNull(clock, "Clock must not be null!");

		this.events = events;
		this.clock = clock;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.AbandonedEventPublications#findAll()
	 */
	@Override
	public Collection<? extends TargetEventPublication> findAll() {
		return events.findAbandonedPublications();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.AbandonedEventPublications#deletePublications(java.util.function.Predicate)
	 */
	@Override
	public void deletePublications(Predicate<EventPublication> filter) {

		var identifiers = findAll().stream()
				.filter(filter)
				.map(TargetEventPublication::getIdentifier)
				.toList();

		events.deletePublications(identifiers);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.AbandonedEventPublications#deletePublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public void deletePublicationsOlderThan(Duration duration) {

		var now = clock.instant();

		events.deleteAbandonedPublicationsBefore(now.minus(duration));
	}
}
