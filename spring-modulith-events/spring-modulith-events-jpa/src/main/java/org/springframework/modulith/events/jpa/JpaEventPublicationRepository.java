/*
 * Copyright 2022-2025 the original author or authors.
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Repository to store {@link TargetEventPublication}s.
 *
 * @author Oliver Drotbohm
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Cora Iberkleid
 */
@Transactional
@Repository
class JpaEventPublicationRepository implements EventPublicationRepository {

	private static String BY_EVENT_AND_LISTENER_ID = """
			select p
			from DefaultJpaEventPublication p
			where
				p.serializedEvent = ?1
				and p.listenerId = ?2
				and p.completionDate is null
			""";

	private static String COMPLETE = """
			select p
			from %s p
			where
				p.completionDate is not null
			order by
				p.publicationDate asc
			""";

	private static String INCOMPLETE = """
			select p
			from DefaultJpaEventPublication p
			where
				p.completionDate is null
			order by
				p.publicationDate asc
			""";

	private static String INCOMPLETE_BEFORE = """
			select p
			from DefaultJpaEventPublication p
			where
				p.completionDate is null
				and p.publicationDate < ?1
			order by
				p.publicationDate asc
			""";

	private static final String MARK_COMPLETED_BY_EVENT_AND_LISTENER_ID = """
			update DefaultJpaEventPublication p
			   set p.completionDate = ?3
			 where p.serializedEvent = ?1
			   and p.listenerId = ?2
			   and p.completionDate is null
			""";

	private static final String MARK_COMPLETED_BY_ID = """
			update DefaultJpaEventPublication p
			   set p.completionDate = ?2
			 where p.id = ?1
			""";

	private static final String DELETE = """
			delete
			  from DefaultJpaEventPublication p
			 where p.id in ?1
			""";

	private static final String DELETE_BY_EVENT_AND_LISTENER_ID = """
			delete DefaultJpaEventPublication p
			 where p.serializedEvent = ?1
			   and p.listenerId = ?2
			""";

	private static final String DELETE_BY_ID = """
			delete
			  from DefaultJpaEventPublication p
			 where p.id = ?1
			""";

	private static final String DELETE_COMPLETED = """
			delete
			from %s p
			where
				p.completionDate is not null
			""";

	private static final String DELETE_COMPLETED_BEFORE = """
			delete
			from %s p
			where
				p.completionDate < ?1
			""";

	private static final int DELETE_BATCH_SIZE = 100;

	private final EntityManager entityManager;
	private final EventSerializer serializer;
	private final CompletionMode completionMode;

	private final String getCompleted, deleteCompleted, deleteCompletedBefore;

	/**
	 * Creates a new {@link JpaEventPublicationRepository} for the given {@link EntityManager} and
	 * {@link EventSerializer}.
	 *
	 * @param entityManager must not be {@literal null}.
	 * @param serializer must not be {@literal null}.
	 */
	public JpaEventPublicationRepository(EntityManager entityManager, EventSerializer serializer,
			CompletionMode completionMode) {

		Assert.notNull(entityManager, "EntityManager must not be null!");
		Assert.notNull(serializer, "EventSerializer must not be null!");
		Assert.notNull(completionMode, "Completion mode must not be null!");

		this.entityManager = entityManager;
		this.serializer = serializer;
		this.completionMode = completionMode;

		var archiveEntityName = getCompletedEntityType().getSimpleName();

		this.getCompleted = COMPLETE.formatted(archiveEntityName);
		this.deleteCompleted = DELETE_COMPLETED.formatted(archiveEntityName);
		this.deleteCompletedBefore = DELETE_COMPLETED_BEFORE.formatted(archiveEntityName);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#create(org.springframework.modulith.events.EventPublication)
	 */
	@Override
	public TargetEventPublication create(TargetEventPublication publication) {

		entityManager.persist(domainToEntity(publication));

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		var serializedEvent = serializeEvent(event);
		var identifierValue = identifier.getValue();

		if (completionMode == CompletionMode.DELETE) {

			entityManager.createQuery(DELETE_BY_EVENT_AND_LISTENER_ID)
					.setParameter(1, serializedEvent)
					.setParameter(2, identifierValue)
					.executeUpdate();

		} else if (completionMode == CompletionMode.ARCHIVE) {

			var publication = entityManager.createQuery(BY_EVENT_AND_LISTENER_ID, JpaEventPublication.getIncompleteType())
					.setParameter(1, serializedEvent)
					.setParameter(2, identifierValue)
					.getSingleResult();

			entityManager.remove(publication);
			entityManager.persist(publication.archive(completionDate));

		} else {

			entityManager.createQuery(MARK_COMPLETED_BY_EVENT_AND_LISTENER_ID)
					.setParameter(1, serializedEvent)
					.setParameter(2, identifierValue)
					.setParameter(3, completionDate)
					.executeUpdate();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markCompleted(java.util.UUID, java.time.Instant)
	 */
	@Override
	public void markCompleted(UUID identifier, Instant completionDate) {

		if (completionMode == CompletionMode.DELETE) {

			entityManager.createQuery(DELETE_BY_ID)
					.setParameter(1, identifier)
					.executeUpdate();

		} else if (completionMode == CompletionMode.ARCHIVE) {

			var publication = entityManager.find(JpaEventPublication.getIncompleteType(), identifier);

			entityManager.remove(publication);
			entityManager.persist(publication.archive(completionDate));

		} else {

			entityManager.createQuery(MARK_COMPLETED_BY_ID)
					.setParameter(1, identifier)
					.setParameter(2, completionDate)
					.executeUpdate();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#findIncompletePublications()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublications() {

		return entityManager.createQuery(INCOMPLETE, JpaEventPublication.getIncompleteType())
				.getResultStream()
				.map(this::entityToDomain)
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsPublishedBefore(java.time.Instant)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {

		return entityManager.createQuery(INCOMPLETE_BEFORE, JpaEventPublication.getIncompleteType())
				.setParameter(1, instant)
				.getResultStream()
				.map(this::entityToDomain)
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier( //
			Object event, PublicationTargetIdentifier targetIdentifier) {

		return findEntityBySerializedEventAndListenerIdAndCompletionDateNull(event, targetIdentifier)
				.map(this::entityToDomain);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findCompletedPublications()
	 */
	@Override
	public List<TargetEventPublication> findCompletedPublications() {

		var type = getCompletedEntityType();

		return entityManager.createQuery(getCompleted, type)
				.getResultList()
				.stream()
				.map(this::entityToDomain)
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deletePublications(java.util.List)
	 */
	@Override
	public void deletePublications(List<UUID> identifiers) {

		batch(identifiers, DELETE_BATCH_SIZE).forEach(it -> {
			entityManager.createQuery(DELETE).setParameter(1, identifiers).executeUpdate();
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	public void deleteCompletedPublications() {
		entityManager.createQuery(deleteCompleted).executeUpdate();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		entityManager.createQuery(deleteCompletedBefore)
				.setParameter(1, instant)
				.executeUpdate();
	}

	/**
	 * Returns the type representing completed event publications.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	public Class<? extends JpaEventPublication> getCompletedEntityType() {
		return JpaEventPublication.getCompletedType(completionMode);
	}

	private Optional<? extends JpaEventPublication> findEntityBySerializedEventAndListenerIdAndCompletionDateNull( //
			Object event, PublicationTargetIdentifier listenerId) {

		var serializedEvent = serializeEvent(event);

		var query = entityManager.createQuery(BY_EVENT_AND_LISTENER_ID, JpaEventPublication.getIncompleteType())
				.setParameter(1, serializedEvent)
				.setParameter(2, listenerId.getValue());

		return query.getResultStream().findFirst();
	}

	private String serializeEvent(Object event) {
		return serializer.serialize(event).toString();
	}

	private JpaEventPublication domainToEntity(TargetEventPublication domain) {

		var event = domain.getEvent();

		return JpaEventPublication.of(domain.getIdentifier(), domain.getPublicationDate(),
				domain.getTargetIdentifier().getValue(), serializeEvent(event), event.getClass());
	}

	private TargetEventPublication entityToDomain(JpaEventPublication entity) {
		return new JpaEventPublicationAdapter(entity, serializer);
	}

	private static <T> List<List<T>> batch(List<T> input, int batchSize) {

		var inputSize = input.size();

		return IntStream.range(0, (inputSize + batchSize - 1) / batchSize)
				.mapToObj(i -> input.subList(i * batchSize, Math.min((i + 1) * batchSize, inputSize)))
				.toList();
	}

	private static class JpaEventPublicationAdapter implements TargetEventPublication {

		private final JpaEventPublication publication;
		private final EventSerializer serializer;
		private Object deserializedEvent;

		/**
		 * Creates a new {@link JpaEventPublicationAdapter} for the given {@link JpaEventPublication} and
		 * {@link EventSerializer}.
		 *
		 * @param publication must not be {@literal null}.
		 * @param serializer must not be {@literal null}.
		 */
		public JpaEventPublicationAdapter(JpaEventPublication publication, EventSerializer serializer) {

			Assert.notNull(publication, "JpaEventPublication must not be null!");
			Assert.notNull(serializer, "EventSerializer must not be null!");

			this.publication = publication;
			this.serializer = serializer;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getPublicationIdentifier()
		 */
		@Override
		public UUID getIdentifier() {
			return publication.id;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getEvent()
		 */
		@Override
		public Object getEvent() {

			if (deserializedEvent == null) {
				this.deserializedEvent = serializer.deserialize(publication.serializedEvent, publication.eventType);
			}

			return deserializedEvent;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getTargetIdentifier()
		 */
		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(publication.listenerId);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getPublicationDate()
		 */
		@Override
		public Instant getPublicationDate() {
			return publication.publicationDate;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.CompletableEventPublication#getCompletionDate()
		 */
		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(publication.completionDate);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.CompletableEventPublication#isPublicationCompleted()
		 */
		@Override
		public boolean isPublicationCompleted() {
			return publication.completionDate != null;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.Completable#markCompleted(java.time.Instant)
		 */
		@Override
		public void markCompleted(Instant instant) {
			this.publication.completionDate = instant;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof JpaEventPublicationAdapter that)) {
				return false;
			}

			return Objects.equals(publication, that.publication)
					&& Objects.equals(serializer, that.serializer);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(publication, serializer);
		}
	}
}
