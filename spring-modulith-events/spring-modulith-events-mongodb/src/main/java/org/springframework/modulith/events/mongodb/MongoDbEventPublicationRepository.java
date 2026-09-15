/*
 * Copyright 2022-2026 the original author or authors.
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
		var update = Update.update(COMPLETION_DATE, completionDate).set(STATUS, Status.COMPLETED);

		if (completionMode == CompletionMode.DELETE) {

			mongoTemplate.remove(query, MongoDbEventPublication.class, collection);

		} else if (completionMode == CompletionMode.ARCHIVE) {

			var ids = mongoTemplate.findDistinct(query(criteria), Fields.UNDERSCORE_ID, collection, UUID.class);

			archiveTerminal(ids, completionDate, Status.COMPLETED);

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
		var update = Update.update(COMPLETION_DATE, completionDate)
				.set(STATUS, Status.COMPLETED);

		if (completionMode == CompletionMode.DELETE) {

			mongoTemplate.remove(query, MongoDbEventPublication.class, collection);

		} else if (completionMode == CompletionMode.ARCHIVE) {

			archiveTerminal(List.of(identifier), completionDate, Status.COMPLETED);

		} else {
			mongoTemplate.findAndModify(query, update, MongoDbEventPublication.class, collection);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markAbandoned(java.util.UUID, java.time.Instant, org.springframework.modulith.events.EventPublication.Status)
	 */
	@Override
	public boolean markAbandoned(UUID identifier, Instant instant, @Nullable Status expectedCurrentStatus) {

		var criteria = expectedCurrentStatus == null
				? where(ID).is(identifier).and(COMPLETION_DATE).isNull()
				: new Criteria().andOperator(where(ID).is(identifier), byStatus(expectedCurrentStatus));

		var query = query(criteria);
		var update = Update.update(COMPLETION_DATE, instant).set(STATUS, Status.ABANDONED);

		if (completionMode == CompletionMode.DELETE) {

			return mongoTemplate.remove(query, MongoDbEventPublication.class, collection).getDeletedCount() > 0;

		} else if (completionMode == CompletionMode.ARCHIVE) {

			if (expectedCurrentStatus == null) {

				archiveTerminal(List.of(identifier), instant, Status.ABANDONED);
				return true;
			}

			var options = FindAndModifyOptions.options().returnNew(true);
			var updated = mongoTemplate.findAndModify(query, update, options, MongoDbEventPublication.class, collection);

			if (updated == null) {
				return false;
			}

			mongoTemplate.save(updated, archiveCollection);
			mongoTemplate.remove(query(where(ID).is(identifier)), MongoDbEventPublication.class, collection);

			return true;

		} else {
			return mongoTemplate.findAndModify(query, update, MongoDbEventPublication.class, collection) != null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markFailed(java.util.UUID)
	 */
	@Override
	public void markFailed(UUID identifier) {

		var query = query(where(ID).is(identifier)
				.and(STATUS).ne(Status.FAILED)
				.and(COMPLETION_DATE).isNull());

		var update = Update.update(STATUS, Status.FAILED);

		mongoTemplate.findAndModify(query, update, MongoDbEventPublication.class, collection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markResubmitted(java.util.UUID, java.time.Instant)
	 */
	@Override
	public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {

		var query = query(where(ID).is(identifier).and(STATUS).ne(Status.RESUBMITTED).and(COMPLETION_DATE).isNull());
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
		return readMapped(defaultQuery(byStatus(Status.COMPLETED)), archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findAbandonedPublications()
	 */
	@Override
	public List<TargetEventPublication> findAbandonedPublications() {
		return readMapped(defaultQuery(byStatus(Status.ABANDONED)), archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findFailedPublications(org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria)
	 */
	@Override
	public List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {

		var statusFailed = byStatus(Status.FAILED);
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

		var query = defaultQuery(baseCriteria);

		return readMapped(limit != -1 ? query.limit((int) limit) : query);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findByStatus(org.springframework.modulith.events.EventPublication.Status)
	 */
	@Override
	public List<TargetEventPublication> findByStatus(Status status) {

		var collection = status.isTerminal() ? archiveCollection : this.collection;

		return readMapped(defaultQuery(byStatus(status)), collection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#countByStatus(org.springframework.modulith.events.EventPublication.Status)
	 */
	@Override
	public int countByStatus(Status status) {

		var collection = status.isTerminal() ? archiveCollection : this.collection;

		return (int) mongoTemplate.count(query(byStatus(status)), MongoDbEventPublication.class, collection);
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
		mongoTemplate.remove(query(byStatus(Status.COMPLETED)), MongoDbEventPublication.class, archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		var criteria = new Criteria().andOperator(byStatus(Status.COMPLETED), where(COMPLETION_DATE).lt(instant));

		mongoTemplate.remove(query(criteria), MongoDbEventPublication.class, archiveCollection);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deleteAbandonedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteAbandonedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		var criteria = new Criteria().andOperator(byStatus(Status.ABANDONED), where(COMPLETION_DATE).lt(instant));

		mongoTemplate.remove(query(criteria), MongoDbEventPublication.class, archiveCollection);
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

	private static Criteria byStatus(Status status) {

		Assert.notNull(status, "Status must not be null!");

		return switch (status) {

			// Any publication with a completion date is considered completed unless explicitly abandoned. Older
			// versions did not consistently update the stored status on completion.
			case COMPLETED -> where(COMPLETION_DATE).ne(null).and(STATUS).ne(Status.ABANDONED);

			case ABANDONED -> where(STATUS).is(status);

			default -> where(STATUS).is(status).and(COMPLETION_DATE).isNull();
		};
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

	private void archiveTerminal(Collection<UUID> identifiers, Instant now, Status status) {

		Assert.isTrue(!archiveCollection.equals(collection),
				"Archive collection must not be identical to the default collection!");

		if (identifiers.isEmpty()) {
			return;
		}

		var aggregation = newAggregation(MongoDbEventPublication.class,

				match(where(ID).in(identifiers).and(COMPLETION_DATE).isNull()),

				addFields()
						.addFieldWithValue(COMPLETION_DATE, now)
						.addFieldWithValue(STATUS, status.name())
						.build(),

				merge()
						.intoCollection(archiveCollection)
						.on(ID)
						.whenMatched(WhenDocumentsMatch.keepExistingDocument())
						.build())
								.withOptions(newAggregationOptions().skipOutput().build());

		mongoTemplate.aggregate(aggregation, collection, Document.class);
		mongoTemplate.remove(query(where(ID).in(identifiers)), MongoDbEventPublication.class, collection);
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

			if (publication.status == Status.ABANDONED) {
				return Status.ABANDONED;
			}

			return publication.completionDate != null ? Status.COMPLETED : publication.status;
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
