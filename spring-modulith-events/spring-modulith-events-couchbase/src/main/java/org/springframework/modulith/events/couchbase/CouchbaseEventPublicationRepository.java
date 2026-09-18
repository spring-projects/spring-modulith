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
package org.springframework.modulith.events.couchbase;

import static org.springframework.data.couchbase.core.query.Query.query;
import static org.springframework.data.couchbase.core.query.QueryCriteria.where;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.query.Query;
import org.springframework.data.couchbase.core.query.QueryCriteria;
import org.springframework.data.domain.Sort;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Repository to store {@link TargetEventPublication}s in a Couchbase DB.
 *
 * @author Oliver Drotbohm
 * @author Alexandre Vigneron
 */
@Transactional
class CouchbaseEventPublicationRepository implements EventPublicationRepository {

	private static final String COMPLETION_DATE = "completionDate";
	private static final String ID = "META().id";
	private static final String LISTENER_ID = "listenerId";
	private static final String PUBLICATION_DATE = "publicationDate";
	private static final String STATUS = "status";

	private static final Sort DEFAULT_SORT = Sort.by(PUBLICATION_DATE).ascending();

	static final String BASE_COLLECTION = "EVENT_PUBLICATION";
	static final String ARCHIVE_COLLECTION = "EVENT_PUBLICATION_ARCHIVE";

	private final CouchbaseTemplate couchbaseTemplate;
	private final CompletionMode completionMode;
	private final String collection, archiveCollection;

	/**
	 * Creates a new {@link CouchbaseEventPublicationRepository} for the given {@link CouchbaseTemplate}.
	 *
	 * @param couchbaseTemplate must not be {@literal null}.
	 * @param completionMode must not be {@literal null}.
	 */
	public CouchbaseEventPublicationRepository(CouchbaseTemplate couchbaseTemplate, CompletionMode completionMode) {

		Assert.notNull(couchbaseTemplate, "CouchbaseTemplate must not be null!");
		Assert.notNull(completionMode, "Completion mode must not be null!");

		this.couchbaseTemplate = couchbaseTemplate;
		this.completionMode = completionMode;
		this.collection = BASE_COLLECTION;
		this.archiveCollection = completionMode == CompletionMode.ARCHIVE ? ARCHIVE_COLLECTION : collection;
	}

	@Override
	public TargetEventPublication create(TargetEventPublication publication) {

		couchbaseTemplate.upsertById(CouchbaseEventPublication.class)
				.inCollection(collection)
				.one(domainToDocument(publication));

		return publication;
	}

	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		findIncompletePublicationsByEventAndTargetIdentifier(event, identifier)
				.map(TargetEventPublication::getIdentifier)
				.ifPresent(id -> markCompleted(id, completionDate));
	}

	@Override
	public void markCompleted(UUID identifier, Instant completionDate) {
		var criteria = where(ID).is(identifier.toString()).and(COMPLETION_DATE).isMissing();

		if (completionMode == CompletionMode.DELETE) {

			couchbaseTemplate.removeByQuery(CouchbaseEventPublication.class)
					.inCollection(collection)
					.matching(criteria)
					.all();

		} else if (completionMode == CompletionMode.ARCHIVE) {

			markCompleted(criteria, completionDate);

		} else {
			updateFirst(criteria, collection, publication -> publication.markCompleted(completionDate));
		}
	}

	@Override
	public void markFailed(UUID identifier) {

		var criteria = where(ID).is(identifier.toString()).and(STATUS).ne(Status.FAILED);

		updateFirst(criteria, collection, CouchbaseEventPublication::markFailed);
	}

	@Override
	public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {

		var criteria = where(ID).is(identifier.toString()).and(STATUS).ne(Status.RESUBMITTED);

		return updateFirst(criteria, collection, publication -> publication.markResubmitted(resubmissionDate));
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublications() {
		return readMapped(defaultQuery(where(COMPLETION_DATE).isMissing()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {
		return readMapped(defaultQuery(where(COMPLETION_DATE).isMissing().and(PUBLICATION_DATE).lt(instant.toEpochMilli())));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
			Object event, PublicationTargetIdentifier targetIdentifier) {

		// N1QL cannot parameterize complex sub-objects (CouchbaseDocument fails JsonValue.coerce).
		return readMapped(defaultQuery(where(LISTENER_ID).is(targetIdentifier.getValue()).and(COMPLETION_DATE).isMissing()))
				.stream()
				.filter(it -> eventsMatch(it.getEvent(), event))
				.findFirst();
	}

	@Override
	public List<TargetEventPublication> findCompletedPublications() {
		return readMapped(defaultQuery(where(COMPLETION_DATE).isValued()), archiveCollection);
	}

	@Override
	public List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {

		var statusFailed = where(STATUS).is(Status.FAILED);

		var reference = criteria.getPublicationDateReference();

		if (reference != null) {
			statusFailed = statusFailed.and(PUBLICATION_DATE).lt(reference.toEpochMilli());
		}

		var limit = criteria.getMaxItemsToRead();

		if (limit > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Number of items to read needs to fit into an integer!");
		}

		var query = defaultQuery(statusFailed);

		return readMapped(limit != -1 ? query.limit((int) limit) : query);
	}

	@Override
	public int countByStatus(Status status) {

		var collection = status == Status.COMPLETED && completionMode == CompletionMode.ARCHIVE
				? archiveCollection
				: this.collection;

		return (int) couchbaseTemplate.findByQuery(CouchbaseEventPublication.class)
				.inCollection(collection)
				.matching(where(STATUS).is(status))
				.count();
	}

	@Override
	public void deletePublications(List<UUID> identifiers) {
		var idStrings = identifiers.stream().map(UUID::toString).toArray();

		couchbaseTemplate.removeByQuery(CouchbaseEventPublication.class)
				.inCollection(collection)
				.matching(where(ID).in(idStrings))
				.all();

		couchbaseTemplate.removeByQuery(CouchbaseEventPublication.class)
				.inCollection(archiveCollection)
				.matching(where(ID).in(idStrings))
				.all();
	}

	@Override
	public void deleteCompletedPublications() {
		couchbaseTemplate.removeByQuery(CouchbaseEventPublication.class)
				.inCollection(archiveCollection)
				.matching(where(COMPLETION_DATE).isNotNull())
				.all();
	}

	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		couchbaseTemplate.removeByQuery(CouchbaseEventPublication.class)
				.inCollection(archiveCollection)
				// Convert to millis before because QueryCriteria convert instant to string by default but Couchbase use Long (see InstantToLongConverter)
				.matching(where(COMPLETION_DATE).lt(instant.toEpochMilli()))
				.all();
	}

	private List<TargetEventPublication> readMapped(Query query) {
		return readMapped(query, collection);
	}

	private List<TargetEventPublication> readMapped(Query query, String collection) {

		return couchbaseTemplate.findByQuery(CouchbaseEventPublication.class)
				.inCollection(collection)
				.matching(query)
				.stream()
				.map(CouchbaseEventPublicationRepository::documentToDomain)
				.toList();
	}

	private static CouchbaseEventPublication domainToDocument(TargetEventPublication publication) {

		return new CouchbaseEventPublication(
				publication.getIdentifier(),
				publication.getPublicationDate(),
				publication.getTargetIdentifier().getValue(),
				publication.getEvent(),
				publication.getCompletionDate().orElse(null),
				publication.getStatus(),
				publication.getLastResubmissionDate(),
				publication.getCompletionAttempts());
	}

	private static TargetEventPublication documentToDomain(CouchbaseEventPublication document) {
		return new CouchBaseEventPublicationAdapter(document);
	}

	private static Query defaultQuery(QueryCriteria criteria) {
		return query(criteria).with(DEFAULT_SORT);
	}

	private void markCompleted(QueryCriteria criteria, Instant completionDate) {

		var query = defaultQuery(criteria);
		var matching = couchbaseTemplate.findByQuery(CouchbaseEventPublication.class)
				.inCollection(collection)
				.matching(query)
				.all();

		matching.forEach(publication -> {

			couchbaseTemplate.upsertById(CouchbaseEventPublication.class)
					.inCollection(archiveCollection)
					.one(publication.markCompleted(completionDate));

			couchbaseTemplate.removeById(CouchbaseEventPublication.class)
					.inCollection(collection)
					.one(publication.id.toString());
		});
	}

	private boolean updateFirst(QueryCriteria query, String collection, UnaryOperator<CouchbaseEventPublication> mutator) {

		var result = couchbaseTemplate.findByQuery(CouchbaseEventPublication.class)
				.inCollection(collection)
				.matching(query)
				.first();

		result.ifPresent(it -> couchbaseTemplate.upsertById(CouchbaseEventPublication.class)
				.inCollection(collection)
				.one(mutator.apply(it)));

		return result.isPresent();
	}

	/**
	 * Compares two event objects by their serialized Couchbase representation
	 */
	private boolean eventsMatch(Object stored, Object query) {

		var converter = couchbaseTemplate.getConverter();
		var storedSerialized = converter.convertForWriteIfNeeded(stored);
		var querySerialized = converter.convertForWriteIfNeeded(query);

		if (storedSerialized instanceof CouchbaseDocument storedDoc
				&& querySerialized instanceof CouchbaseDocument queryDoc) {
			return storedDoc.export().equals(queryDoc.export());
		}

		return Objects.equals(storedSerialized, querySerialized);
	}

	private static class CouchBaseEventPublicationAdapter implements TargetEventPublication {

		private final CouchbaseEventPublication publication;

		CouchBaseEventPublicationAdapter(CouchbaseEventPublication publication) {
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
			return publication.status;
		}

		@Override
		public int getCompletionAttempts() {
			return publication.completionAttempts;
		}

		@Override
		public @Nullable Instant getLastResubmissionDate() {
			return publication.lastResubmissionDate;
		}

		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof CouchBaseEventPublicationAdapter that)) {
				return false;
			}

			return Objects.equals(publication, that.publication);
		}

		@Override
		public int hashCode() {
			return Objects.hash(publication);
		}
	}
}
