/*
 * Copyright 2017-2022 the original author or authors.
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

import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A registry to capture event publications to {@link ApplicationListener}s. Allows to register those publications, mark
 * them as completed and lookup incomplete publications.
 *
 * @author Oliver Drotbohm, Björn Kieling, Dmitry Belyaev
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultEventPublicationRegistry implements DisposableBean, EventPublicationRegistry {

	private final @NonNull EventPublicationRepository events;

	/**
	 * Stores {@link EventPublication}s for the given event and {@link ApplicationListener}s.
	 *
	 * @param event must not be {@literal null}.
	 * @param listeners must not be {@literal null}.
	 */
	public void store(Object event, Stream<PublicationTargetIdentifier> listeners) {

		listeners.map(it -> map(event, it))
				.forEach(events::create);
	}

	/**
	 * Returns all {@link EventPublication}s that have not been completed yet.
	 *
	 * @return will never be {@literal null}.
	 */
	public Iterable<EventPublication> findIncompletePublications() {
		return events.findByCompletionDateIsNull();
	}

	/**
	 * Marks the publication for the given event and {@link PublicationTargetIdentifier} as completed.
	 *
	 * @param event must not be {@literal null}.
	 * @param targetIdentifier must not be {@literal null}.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(Object event, PublicationTargetIdentifier targetIdentifier) {

		Assert.notNull(event, "Domain event must not be null!");
		Assert.notNull(targetIdentifier, "Listener identifier must not be null!");

		events.findByEventAndTargetIdentifier(event, targetIdentifier) //
				.map(DefaultEventPublicationRegistry::LOGCompleted) //
				.map(e -> CompletableEventPublication.of(e.getEvent(), e.getTargetIdentifier()))
				.ifPresent(it -> events.updateCompletionDate(it.markCompleted()));
	}

	@Override
	public void destroy() {

		List<EventPublication> publications = events.findByCompletionDateIsNull();

		if (publications.isEmpty()) {

			LOG.info("No publications outstanding!");
			return;
		}

		LOG.info("Shutting down with the following publications left unfinished:");

		for (int i = 0; i < publications.size(); i++) {

			String prefix = (i + 1) == publications.size() ? "└─" : "├─";
			EventPublication it = publications.get(i);

			LOG.info("{} - {} - {}", prefix, it.getEvent().getClass().getName(), it.getTargetIdentifier().getValue());
		}
	}

	private EventPublication map(Object event, PublicationTargetIdentifier targetIdentifier) {

		EventPublication result = CompletableEventPublication.of(event, targetIdentifier);

		LOG.debug("Registering publication of {} for {}.", //
				result.getEvent().getClass().getName(), result.getTargetIdentifier().getValue());

		return result;
	}

	private static EventPublication LOGCompleted(EventPublication publication) {

		LOG.debug("Marking publication of event {} to listener {} completed.", //
				publication.getEvent().getClass().getName(), publication.getTargetIdentifier().getValue());

		return publication;
	}
}
