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
package org.springframework.modulith.events.mongodb;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.aggregation.MergeOperation.WhenDocumentsMatch;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionMode;
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
	private static final String STATUS = "status";
	private static final String COMPLETION_ATTEMPTS = "completionAttempts";
	private static final String LAST_RESUBMISSION_DATE = "lastResubmissionDate";

	private static final Sort DEFAULT_SORT = Sort.by(PUBLICATION_DATE).ascending();

	static final String ARCHIVE_COLLECTION = "event_publication_archive";

	private final MongoTemplate mongoTemplate;
	private final CompletionMode completionMode;
	private final String collection, archiveCollection;

	/**
	 * Creates a new {@link MongoDbEventPublicationRepository} for the given {@link MongoTemplate}.
	 *
	 * @param mongoTemplate must not be {@literal null}.
	 * @param completionMode must not be {@literal null}.
	 */
	public MongoDbEventPublicationRepository(MongoTemplate mongoTemplate, CompletionMode completionMode) {

		Assert.notNull(mongoTemplate, "MongoTemplate must not be null!");
		Assert.notNull(completionMode, "Completion mode must not be null!");

		this.mongoTemplate = mongoTemplate;
		this.completionMode = completionMode;
		this.collection = "event_publication";
		this.archiveCollection = completionMode == CompletionMode.ARCHIVE ? ARCHIVE_COLLECTION : collection;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#create(org.springframework.modulith.events.EventPublication)
	 */
	@Override
	public TargetEventPublication create(TargetEventPublication publication) {

		mongoTemplate.save(domainToDocument(publication), collection);

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		var criteria = byEventAndListenerId(event, identifier);
		var query = defaultQuery(criteria);
		var update = Update.update(COMPLETION_DATE, completionDate);

		if (completionMode == CompletionMode.DELETE) {

			mongoTemplate.remove(query, MongoDbEventPublication.class, collection);

		} else if (completionMode == CompletionMode.ARCHIVE) {

			markCompleted(criteria, completionDate);

		} else {

			mongoTemplate.findAndModify(query, update, MongoDbEventPublication.class, collection);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markCompleted(java.util.UUID, java.time.Instant)
	 */
	@Override
	public void markCompleted(UUID identifier, Instant completionDate) {

		var criteria = where(ID).is(identifier).and(COMPLETION_DATE).isNull();
		var query = query(criteria);
		var update = Update.update(COMPLETION_DATE, completionDate);

		if (completionMode == CompletionMode.DELETE) {

			mongoTemplate.remove(query, MongoDbEventPublication.class, collection);

		} else if (completionMode == CompletionMode.ARCHIVE) {

			markCompleted(criteria, completionDate);

		} else {
			mongoTemplate.findAndModify(query, update, MongoDbEventPublication.class, collection);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markFailed(java.util.UUID)
	 */
	@Override
	public void markFailed(UUID identifier) {

		var query = query(where(ID).is(identifier).and(STATUS).ne(Status.FAILED));
		var update = Update.update(STATUS, Status.FAILED);

		mongoTemplate.findAndModify(query, update, MongoDbEventPublication.class, collection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markResubmitted(java.util.UUID, java.time.Instant)
	 */
	@Override
	public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {

		var query = query(where(ID).is(identifier).and(STATUS).ne(Status.RESUBMITTED));
		var update = Update.update(STATUS, Status.RESUBMITTED)
				.inc(COMPLETION_ATTEMPTS, 1)
				.set(LAST_RESUBMISSION_DATE, resubmissionDate);

		var result = mongoTemplate.updateFirst(query, update, MongoDbEventPublication.class, collection);

		return result.getModifiedCount() == 1;
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

		var results = readMapped(defaultQuery(byEventAndListenerId(event, targetIdentifier)));

		// if there are several events with exactly the same payload we return the oldest one first
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findCompletedPublications()
	 */
	@Override
	public List<TargetEventPublication> findCompletedPublications() {
		return readMapped(defaultQuery(where(COMPLETION_DATE).ne(null)), archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findFailedPublications(org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria)
	 */
	@Override
	public List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {

		var statusFailed = where(STATUS).is(Status.FAILED);
		var noStatusAndCompletionDate = where(STATUS).isNull().and(COMPLETION_DATE).isNull();
		var baseCriteria = new Criteria().orOperator(statusFailed, noStatusAndCompletionDate);

		// Apply date delimiter
		var reference = criteria.getPublicationDateReference();

		if (reference != null) {
			baseCriteria.and(PUBLICATION_DATE).lt(reference);
		}

		// Apply limit
		var limit = criteria.getMaxItemsToRead();

		if (limit > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Number of items to read needs to fit into an integer!");
		}

		return readMapped(defaultQuery(baseCriteria).limit((int) limit));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#countByStatus(org.springframework.modulith.events.EventPublication.Status)
	 */
	@Override
	public int countByStatus(Status status) {

		var collection = status == Status.COMPLETED && completionMode == CompletionMode.ARCHIVE
				? archiveCollection
				: this.collection;

		return (int) mongoTemplate.count(query(where(STATUS).is(status)), MongoDbEventPublication.class, collection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deletePublications(java.util.List)
	 */
	@Override
	public void deletePublications(List<UUID> identifiers) {

		mongoTemplate.remove(query(where(ID).in(identifiers)), MongoDbEventPublication.class, collection);
		mongoTemplate.remove(query(where(ID).in(identifiers)), MongoDbEventPublication.class, archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	public void deleteCompletedPublications() {
		mongoTemplate.remove(query(where(COMPLETION_DATE).ne(null)), MongoDbEventPublication.class, archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		mongoTemplate.remove(query(where(COMPLETION_DATE).lt(instant)), MongoDbEventPublication.class, archiveCollection);
	}

	private List<TargetEventPublication> readMapped(Query query) {
		return readMapped(query, collection);
	}

	private List<TargetEventPublication> readMapped(Query query, String collection) {

		return mongoTemplate.query(MongoDbEventPublication.class)
				.inCollection(collection)
				.matching(query)
				.stream()
				.map(MongoDbEventPublicationRepository::documentToDomain)
				.toList();

	}

	private Criteria byEventAndListenerId(Object event, PublicationTargetIdentifier identifier) {

		var eventAsMongoType = mongoTemplate.getConverter().convertToMongoType(event, TypeInformation.OBJECT);

		return where(EVENT).is(eventAsMongoType) //
				.and(LISTENER_ID).is(identifier.getValue())
				.and(COMPLETION_DATE).isNull();
	}

	private static MongoDbEventPublication domainToDocument(TargetEventPublication publication) {

		return new MongoDbEventPublication( //
				publication.getIdentifier(), //
				publication.getPublicationDate(), //
				publication.getTargetIdentifier().getValue(), //
				publication.getEvent(), //
				publication.getCompletionDate().orElse(null), //
				publication.getStatus(), //
				publication.getLastResubmissionDate(), //
				publication.getCompletionAttempts());
	}

	private static TargetEventPublication documentToDomain(MongoDbEventPublication document) {
		return new MongoDbEventPublicationAdapter(document);
	}

	private static Query defaultQuery(Criteria criteria) {
		return query(criteria).with(DEFAULT_SORT);
	}

	private void markCompleted(Criteria lookup, Instant now) {

		var aggregation = newAggregation(MongoDbEventPublication.class,

				match(lookup),

				addFields()
						.addFieldWithValue(COMPLETION_DATE, now)
						.build(),

				merge()
						.intoCollection(archiveCollection)
						.on(ID)
						.whenMatched(WhenDocumentsMatch.keepExistingDocument())
						.build());

		mongoTemplate
				.aggregate(aggregation, collection, Document.class)
				.forEach(it -> mongoTemplate.remove(query(where(Fields.UNDERSCORE_ID).is(it.get(Fields.UNDERSCORE_ID))),
						collection));
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
		public void markCompleted(Instant instant) {
			this.publication.markCompleted(instant);
		}

		@Override
		public Status getStatus() {

			if (publication.status != null) {
				return publication.status;
			}

			return publication.completionDate != null ? Status.COMPLETED : Status.PUBLISHED;
		}

		@Override
		public int getCompletionAttempts() {
			return publication.completionAttempts;
		}

		@Override
		public @Nullable Instant getLastResubmissionDate() {
			return publication.lastResubmissionDate;
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

	record IdOnly(@Id UUID id) {}
}
