/*
 * Copyright 2017-2024 the original author or authors.
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
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * A registry to capture event publications to {@link ApplicationListener}s. Allows to register those publications, mark
 * them as completed and lookup incomplete publications.
 *
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
public class DefaultEventPublicationRegistry implements DisposableBean, EventPublicationRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEventPublicationRegistry.class);
	private static final String REGISTER = "Registering publication of {} for {}.";

	private final EventPublicationRepository events;
	private final Clock clock;

	/**
	 * Creates a new {@link DefaultEventPublicationRegistry} for the given {@link EventPublicationRepository}.
	 *
	 * @param events must not be {@literal null}.
	 * @param clock must not be {@literal null}.
	 */
	public DefaultEventPublicationRegistry(EventPublicationRepository events, Clock clock) {

		Assert.notNull(events, "EventPublicationRepository must not be null!");
		Assert.notNull(clock, "Clock must not be null!");

		this.events = events;
		this.clock = clock;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#store(java.lang.Object, java.util.stream.Stream)
	 */
	@Override
	public Collection<EventPublication> store(Object event, Stream<PublicationTargetIdentifier> listeners) {

		return listeners.map(it -> EventPublication.of(event, it, clock.instant()))
				.peek(it -> LOGGER.debug(REGISTER, it.getEvent().getClass().getName(), it.getTargetIdentifier().getValue()))
				.map(events::create)
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#findIncompletePublications()
	 */
	@Override
	public Collection<EventPublication> findIncompletePublications() {
		return events.findIncompletePublications();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(Object event, PublicationTargetIdentifier targetIdentifier) {

		Assert.notNull(event, "Domain event must not be null!");
		Assert.notNull(targetIdentifier, "Listener identifier must not be null!");

		LOGGER.debug("Marking publication of event {} to listener {} completed.", //
				event.getClass().getName(), targetIdentifier.getValue());

		events.markCompleted(event, targetIdentifier, clock.instant());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#deleteCompletedPublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public void deleteCompletedPublicationsOlderThan(Duration duration) {

		Assert.notNull(duration, "Duration must not be null!");

		events.deleteCompletedPublicationsBefore(clock.instant().minus(duration));
	};

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	@Override
	public void destroy() {

		List<EventPublication> publications = events.findIncompletePublications();

		if (publications.isEmpty()) {

			LOGGER.info("No publications outstanding!");
			return;
		}

		LOGGER.info("Shutting down with the following publications left unfinished:");

		for (int i = 0; i < publications.size(); i++) {

			String prefix = i + 1 == publications.size() ? "└─" : "├─";
			EventPublication it = publications.get(i);

			LOGGER.info("{} {} - {}", prefix, it.getEvent().getClass().getName(), it.getTargetIdentifier().getValue());
		}
	}
}
