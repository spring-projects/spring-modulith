/*
 * Copyright 2022-2023 the original author or authors.
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
package org.springframework.modulith.events.jpa;

import jakarta.persistence.EntityManager;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository to store {@link EventPublication}s.
 *
 * @author Oliver Drotbohm
 * @author Dmitry Belyaev
 * @author Björn Kieling
 */
@RequiredArgsConstructor
class JpaEventPublicationRepository implements EventPublicationRepository {

	private static String BY_EVENT_AND_LISTENER_ID = """
			select p
			from JpaEventPublication p
				where
					p.serializedEvent = ?1
					and p.listenerId = ?2
					and p.completionDate is null
			""";

	private static String INCOMPLETE = """
			select p
			from JpaEventPublication p
			where
				p.completionDate is null
			""";

	private static final String DELETE_COMPLETED = """
			delete
			from JpaEventPublication p
			where
				p.completionDate is not null
			""";

	private final EntityManager entityManager;
	private final EventSerializer serializer;

	@Override
	@Transactional
	public EventPublication create(EventPublication publication) {

		entityManager.persist(domainToEntity(publication));

		return publication;
	}

	@Override
	@Transactional
	public EventPublication update(CompletableEventPublication publication) {

		var id = publication.getTargetIdentifier().getValue();
		var event = publication.getEvent();

		findEntityBySerializedEventAndListenerIdAndCompletionDateNull(event, id) //
				.ifPresent(entity -> entity.setCompletionDate(publication.getCompletionDate().orElse(null)));

		return publication;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EventPublication> findIncompletePublications() {

		return entityManager.createQuery(INCOMPLETE, JpaEventPublication.class)
				.getResultStream()
				.map(this::entityToDomain)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventPublication> findIncompletePublicationsByEventAndTargetIdentifier( //
			Object event, PublicationTargetIdentifier targetIdentifier) {

		return findEntityBySerializedEventAndListenerIdAndCompletionDateNull(event, targetIdentifier.getValue())
				.map(this::entityToDomain);
	}

	@Override
	@Transactional
	public void deleteCompletedPublications() {
		entityManager.createQuery(DELETE_COMPLETED).executeUpdate();
	}

	private Optional<JpaEventPublication> findEntityBySerializedEventAndListenerIdAndCompletionDateNull( //
			Object event, String listenerId) {

		var serializedEvent = serializeEvent(event);

		var query = entityManager.createQuery(BY_EVENT_AND_LISTENER_ID, JpaEventPublication.class)
				.setParameter(1, serializedEvent)
				.setParameter(2, listenerId);

		return query.getResultStream().findFirst();
	}

	private String serializeEvent(Object event) {
		return serializer.serialize(event).toString();
	}

	private JpaEventPublication domainToEntity(EventPublication domain) {

		return JpaEventPublication.builder() //
				.publicationDate(domain.getPublicationDate()) //
				.listenerId(domain.getTargetIdentifier().getValue()) //
				.serializedEvent(serializeEvent(domain.getEvent())) //
				.eventType(domain.getEvent().getClass()) //
				.build();
	}

	private EventPublication entityToDomain(JpaEventPublication entity) {
		return JpaEventPublicationAdapter.of(entity, serializer);
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "of")
	private static class JpaEventPublicationAdapter implements CompletableEventPublication {

		private final JpaEventPublication publication;
		private final EventSerializer serializer;

		@Override
		public Object getEvent() {
			return serializer.deserialize(publication.getSerializedEvent(), publication.getEventType());
		}

		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(publication.getListenerId());
		}

		@Override
		public Instant getPublicationDate() {
			return publication.getPublicationDate();
		}

		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(publication.getCompletionDate());
		}

		@Override
		public boolean isPublicationCompleted() {
			return publication.getCompletionDate() != null;
		}

		@Override
		public CompletableEventPublication markCompleted() {
			publication.markCompleted();
			return this;
		}
	}
}
