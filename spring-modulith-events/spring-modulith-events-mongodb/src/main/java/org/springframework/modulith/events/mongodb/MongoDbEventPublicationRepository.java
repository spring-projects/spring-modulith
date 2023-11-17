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
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.TypeInformation;
import org.springframework.modulith.events.core.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.util.Assert;

/**
 * Repository to store {@link EventPublication}s in a MongoDB.
 *
 * @author Björn Kieling
 * @author Dmitry Belyaev
 * @author Oliver Drotbohm
 */
class MongoDbEventPublicationRepository implements EventPublicationRepository {

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
	public EventPublication create(EventPublication publication) {

		mongoTemplate.save(domainToDocument(publication));

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		var criteria = byEventAndListenerId(event, identifier);
		var update = Update.update("completionDate", completionDate);

		mongoTemplate.findAndModify(defaultQuery(criteria), update, MongoDbEventPublication.class);
	}

	@Override
	public List<EventPublication> findIncompletePublications() {

		var query = defaultQuery(where("completionDate").isNull());

		return mongoTemplate.find(query, MongoDbEventPublication.class).stream() //
				.map(this::documentToDomain) //
				.toList();
	}

	@Override
	public Optional<EventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
			Object event, PublicationTargetIdentifier targetIdentifier) {

		var documents = findDocumentsByEventAndTargetIdentifierAndCompletionDateNull(event, targetIdentifier);
		var results = documents
				.stream() //
				.map(this::documentToDomain) //
				.toList();

		// if there are several events with exactly the same payload we return the oldest one first
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	public void deleteCompletedPublications() {
		mongoTemplate.remove(query(where("completionDate").ne(null)), MongoDbEventPublication.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		mongoTemplate.remove(query(where("completionDate").lt(instant)), MongoDbEventPublication.class);
	}

	private List<MongoDbEventPublication> findDocumentsByEventAndTargetIdentifierAndCompletionDateNull( //
			Object event, PublicationTargetIdentifier targetIdentifier) {

		var criteria = byEventAndListenerId(event, targetIdentifier);
		var query = defaultQuery(criteria);

		return mongoTemplate.find(query, MongoDbEventPublication.class);
	}

	private Criteria byEventAndListenerId(Object event, PublicationTargetIdentifier identifier) {

		var eventAsMongoType = mongoTemplate.getConverter().convertToMongoType(event, TypeInformation.OBJECT);

		return where("event").is(eventAsMongoType) //
				.and("listenerId").is(identifier.getValue())
				.and("completionDate").isNull();
	}

	private MongoDbEventPublication domainToDocument(EventPublication publication) {

		return new MongoDbEventPublication( //
				publication.getIdentifier(), //
				publication.getPublicationDate(), //
				publication.getTargetIdentifier().getValue(), //
				publication.getEvent());
	}

	private EventPublication documentToDomain(MongoDbEventPublication document) {
		return new MongoDbEventPublicationAdapter(document);
	}

	private static Query defaultQuery(CriteriaDefinition criteria) {
		return query(criteria).with(Sort.by("publicationDate").ascending());
	}

	private static class MongoDbEventPublicationAdapter implements EventPublication {

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
