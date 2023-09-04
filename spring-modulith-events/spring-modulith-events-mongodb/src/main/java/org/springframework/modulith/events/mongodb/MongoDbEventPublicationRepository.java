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
package org.springframework.modulith.events.mongodb;

import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.TypeInformation;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Repository to store {@link TargetEventPublication}s in a MongoDB.
 *
 * @author Björn Kieling
 * @author Dmitry Belyaev
 * @author Oliver Drotbohm
 */
@Transactional
class MongoDbEventPublicationRepository implements EventPublicationRepository {

	private static final String COMPLETION_DATE = "completionDate";
	private static final String EVENT = "event";
	private static final String ID = "id";
	private static final String LISTENER_ID = "listenerId";
	private static final String PUBLICATION_DATE = "publicationDate";

	private static final Sort DEFAULT_SORT = Sort.by(PUBLICATION_DATE).ascending();

	private final MongoTemplate mongoTemplate;

	/**
	 * Creates a new {@link MongoDbEventPublicationRepository} for the given {@link MongoTemplate}.
	 *
	 * @param mongoTemplate must not be {@literal null}.
	 */
	public MongoDbEventPublicationRepository(MongoTemplate mongoTemplate) {

		Assert.notNull(mongoTemplate, "MongoTemplate must not be null!");

		this.mongoTemplate = mongoTemplate;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#create(org.springframework.modulith.events.EventPublication)
	 */
	@Override
	public TargetEventPublication create(TargetEventPublication publication) {

		mongoTemplate.save(domainToDocument(publication));

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		var update = Update.update(COMPLETION_DATE, completionDate);

		mongoTemplate.updateFirst(byEventAndListenerId(event, identifier), update, MongoDbEventPublication.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublications()
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublications() {
		return readMapped(defaultQuery(where(COMPLETION_DATE).isNull()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsPublishedBefore(java.time.Instant)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {
		return readMapped(defaultQuery(where(COMPLETION_DATE).isNull().and(PUBLICATION_DATE).lt(instant)));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
			Object event, PublicationTargetIdentifier targetIdentifier) {

		var results = readMapped(byEventAndListenerId(event, targetIdentifier));

		// if there are several events with exactly the same payload we return the oldest one first
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deletePublications(java.util.List)
	 */
	@Override
	public void deletePublications(List<UUID> identifiers) {
		mongoTemplate.remove(query(where(ID).in(identifiers)), MongoDbEventPublication.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	public void deleteCompletedPublications() {
		mongoTemplate.remove(query(where(COMPLETION_DATE).ne(null)), MongoDbEventPublication.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		mongoTemplate.remove(query(where(COMPLETION_DATE).lt(instant)), MongoDbEventPublication.class);
	}

	private List<TargetEventPublication> readMapped(Query query) {

		return mongoTemplate.query(MongoDbEventPublication.class)
				.matching(query)
				.stream()
				.map(MongoDbEventPublicationRepository::documentToDomain)
				.toList();
	}

	private Query byEventAndListenerId(Object event, PublicationTargetIdentifier identifier) {

		var eventAsMongoType = mongoTemplate.getConverter().convertToMongoType(event, TypeInformation.OBJECT);

		return defaultQuery(where(EVENT).is(eventAsMongoType) //
				.and(LISTENER_ID).is(identifier.getValue())
				.and(COMPLETION_DATE).isNull());
	}

	private static MongoDbEventPublication domainToDocument(TargetEventPublication publication) {

		return new MongoDbEventPublication( //
				publication.getIdentifier(), //
				publication.getPublicationDate(), //
				publication.getTargetIdentifier().getValue(), //
				publication.getEvent());
	}

	private static TargetEventPublication documentToDomain(MongoDbEventPublication document) {
		return new MongoDbEventPublicationAdapter(document);
	}

	private static Query defaultQuery(Criteria criteria) {
		return query(criteria).with(DEFAULT_SORT);
	}

	private static class MongoDbEventPublicationAdapter implements TargetEventPublication {

		private final MongoDbEventPublication publication;

		MongoDbEventPublicationAdapter(MongoDbEventPublication publication) {
			this.publication = publication;
		}

		@Override
		public UUID getIdentifier() {
			return publication.id;
		}

		@Override
		public Object getEvent() {
			return publication.event;
		}

		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(publication.listenerId);
		}

		@Override
		public Instant getPublicationDate() {
			return publication.publicationDate;
		}

		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(publication.completionDate);
		}

		@Override
		public boolean isPublicationCompleted() {
			return publication.completionDate != null;
		}

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

			if (!(obj instanceof MongoDbEventPublicationAdapter that)) {
				return false;
			}

			return Objects.equals(publication, that.publication);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(publication);
		}
	}
}
