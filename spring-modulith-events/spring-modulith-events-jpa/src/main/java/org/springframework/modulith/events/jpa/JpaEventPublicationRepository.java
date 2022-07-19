/*
 * Copyright 2022 the original author or authors.
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.annotation.Transactional;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * Repository to store {@link EventPublication}s.
 *
 * @author Oliver Drotbohm, Dmitry Belyaev, Björn Kieling
 */
@RequiredArgsConstructor
public class JpaEventPublicationRepository implements EventPublicationRepository {

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
	public EventPublication updateCompletionDate(CompletableEventPublication publication) {

		findEntityBySerializedEventAndListenerId(publication.getEvent(),
				publication.getTargetIdentifier().getValue()).ifPresent(entity -> {
			entity.setCompletionDate(publication.getCompletionDate().orElse(null));
			entityManager.flush();
		});
		return publication;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EventPublication> findByCompletionDateIsNull() {

		String query = "select p from JpaEventPublication p where p.completionDate is null";

		return entityManager.createQuery(query, JpaEventPublication.class).getResultList().stream()
				.map(this::entityToDomain).collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventPublication> findByEventAndTargetIdentifier(Object event,
			PublicationTargetIdentifier targetIdentifier) {

		Optional<JpaEventPublication> result = findEntityBySerializedEventAndListenerId(event, targetIdentifier.getValue());
		return result.map(this::entityToDomain);
	}

	private Optional<JpaEventPublication> findEntityBySerializedEventAndListenerId(Object event, String listenerId) {
		String query = "select p from JpaEventPublication p where p.serializedEvent = ?1 and p.listenerId = ?2";
		String serializedEvent = serializeEvent(event);
		TypedQuery<JpaEventPublication> typedQuery = entityManager.createQuery(query, JpaEventPublication.class)
				.setParameter(1, serializedEvent).setParameter(2, listenerId);
		JpaEventPublication resultEntity = typedQuery.getSingleResult();
		return Optional.ofNullable(resultEntity);
	}

	private String serializeEvent(Object event) {
		return serializer.serialize(event).toString();
	}

	private JpaEventPublication domainToEntity(EventPublication domain) {
		String serializedEvent = serializeEvent(domain.getEvent());
		return JpaEventPublication.builder().id(UUID.randomUUID()).publicationDate(domain.getPublicationDate())
				.listenerId(domain.getTargetIdentifier().getValue()).serializedEvent(serializedEvent)
				.eventType(domain.getEvent().getClass()).build();
	}

	private CompletableEventPublication entityToDomain(JpaEventPublication entity) {
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
