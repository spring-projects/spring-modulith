/*
 * Copyright 2017-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events.jpa;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRegistry;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA based {@link EventPublicationRegistry}.
 *
 * @author Oliver Gierke
 */
@Slf4j
@RequiredArgsConstructor
class JpaEventPublicationRegistry implements EventPublicationRegistry, DisposableBean {

	private final @NonNull JpaEventPublicationRepository events;
	private final @NonNull EventSerializer serializer;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.events.EventPublicationRegistry#store(java.lang.Object, java.util.Collection)
	 */
	@Override
	public void store(Object event, Stream<PublicationTargetIdentifier> listeners) {

		listeners.map(it -> CompletableEventPublication.of(event, it)) //
				.map(this::map) //
				.forEach(it -> events.create(it));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.events.EventPublicationRegistry#findIncompletePublications()
	 */
	@Override
	public Iterable<EventPublication> findIncompletePublications() {

		List<EventPublication> result = events.findByCompletionDateIsNull().stream() //
				.map(it -> JpaEventPublicationAdapter.of(it, serializer)) //
				.collect(Collectors.toList());

		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.events.EventPublicationRegistry#markCompleted(java.lang.Object, org.springframework.events.ListenerId)
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(Object event, PublicationTargetIdentifier listener) {

		Assert.notNull(event, "Domain event must not be null!");
		Assert.notNull(listener, "Listener identifier must not be null!");

		events.findBySerializedEventAndListenerId(serializer.serialize(event), listener.toString()) //
				.map(JpaEventPublicationRegistry::logCompleted) //
				.ifPresent(it -> events.update(it.markCompleted()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	@Override
	public void destroy() throws Exception {

		List<JpaEventPublication> publications = events.findByCompletionDateIsNull();

		if (publications.isEmpty()) {

			LOG.info("No publications outstanding!");
			return;
		}

		LOG.info("Shutting down with the following publications left unfinished:");

		for (int i = 0; i < publications.size(); i++) {

			String prefix = (i + 1) == publications.size() ? "└─" : "├─";
			JpaEventPublication it = publications.get(i);

			LOG.info("{} {} - {} - {}", prefix, it.getId(), it.getEventType().getName(), it.getListenerId());
		}
	}

	private JpaEventPublication map(EventPublication publication) {

		JpaEventPublication result = JpaEventPublication.builder() //
				.eventType(publication.getEvent().getClass()) //
				.publicationDate(publication.getPublicationDate()) //
				.listenerId(publication.getTargetIdentifier().toString()) //
				.serializedEvent(serializer.serialize(publication.getEvent()).toString()) //
				.build();

		LOG.debug("Registering publication of {} with id {} for {}.", //
				result.getEventType(), result.getId(), result.getListenerId());

		return result;
	}

	private static JpaEventPublication logCompleted(JpaEventPublication publication) {

		LOG.debug("Marking publication of event {} with id {} to listener {} completed.", //
				publication.getEventType(), publication.getId(), publication.getListenerId());

		return publication;
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "of")
	static class JpaEventPublicationAdapter implements EventPublication {

		private final JpaEventPublication publication;
		private final EventSerializer serializer;

		private Object deserializedEvent;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.events.EventPublication#getEvent()
		 */
		@Override
		public Object getEvent() {

			if (deserializedEvent == null) {
				this.deserializedEvent = serializer.deserialize(publication.getSerializedEvent(), publication.getEventType());
			}

			return deserializedEvent;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.events.EventPublication#getListenerId()
		 */
		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(publication.getListenerId());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.events.EventPublication#getPublicationDate()
		 */
		@Override
		public Instant getPublicationDate() {
			return publication.getPublicationDate();
		}
	}
}
