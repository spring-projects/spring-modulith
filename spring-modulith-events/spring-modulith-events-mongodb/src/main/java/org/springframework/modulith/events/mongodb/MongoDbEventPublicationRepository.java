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
package org.springframework.modulith.events.mongodb;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.TypeInformation;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.PublicationTargetIdentifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * Repository to store {@link EventPublication}s in a MongoDB.
 *
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
public class MongoDbEventPublicationRepository implements EventPublicationRepository {

	private final MongoTemplate mongoTemplate;

	public MongoDbEventPublicationRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public EventPublication create(EventPublication publication) {
		mongoTemplate.save(domainToDocument(publication));
		return publication;
	}

	@Override
	public EventPublication update(CompletableEventPublication publication) {
		findDocumentsByEventAndTargetIdentifier(publication.getEvent(), publication.getTargetIdentifier())
				.stream()
				.findFirst()
				.ifPresent(document -> {
					document.setCompletionDate(publication.getCompletionDate().orElse(null));
					mongoTemplate.save(document);
				});
		return publication;
	}

	@Override
	public List<EventPublication> findIncompletePublications() {
		var query = Query.query(Criteria.where("completionDate").isNull());
		return mongoTemplate.find(query, MongoDbEventPublication.class).stream() //
				.map(this::documentToDomain) //
				.collect(Collectors.toList());
	}

	@Override
	public Optional<EventPublication> findByEventAndTargetIdentifier(
			Object event, PublicationTargetIdentifier targetIdentifier) {

		var documents = findDocumentsByEventAndTargetIdentifier(event, targetIdentifier);
		var results = documents
				.stream() //
				.map(this::documentToDomain) //
				.toList();

		// if there are several events with exactly the same payload we return the oldest one first
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	private List<MongoDbEventPublication> findDocumentsByEventAndTargetIdentifier(
			Object event, PublicationTargetIdentifier targetIdentifier) {

		// we need to enforce writing of the type information
		var eventAsMongoType = mongoTemplate.getConverter().convertToMongoType(event, TypeInformation.of(Object.class));
		var query = Query //
				.query(Criteria //
						.where("event").is(eventAsMongoType) //
						.and("listenerId").is(targetIdentifier.getValue())) //
				.with(Sort.by("publicationDate").ascending());
		return mongoTemplate.find(query, MongoDbEventPublication.class);
	}

	private MongoDbEventPublication domainToDocument(EventPublication publication) {
		return new MongoDbEventPublication( //
				publication.getPublicationDate(), //
				publication.getTargetIdentifier().getValue(), //
				publication.getEvent());
	}

	private CompletableEventPublication documentToDomain(MongoDbEventPublication document) {
		return MongoDbEventPublicationAdapter.of(document);
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "of")
	private static class MongoDbEventPublicationAdapter implements CompletableEventPublication {

		private final MongoDbEventPublication publication;

		@Override
		public Object getEvent() {
			return publication.getEvent();
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
			publication.setCompletionDate(Instant.now());
			return this;
		}
	}

}
