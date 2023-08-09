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

import static org.assertj.core.api.Assertions.*;

import lombok.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.modulith.events.core.EventPublication;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
@DataMongoTest
@ContextConfiguration(classes = TestApplication.class)
class MongoDbEventPublicationRepositoryTest {

	private static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");

	@Autowired MongoTemplate mongoTemplate;

	MongoDbEventPublicationRepository repository;

	@BeforeEach
	void setUp() {
		repository = new MongoDbEventPublicationRepository(mongoTemplate);
	}

	@AfterEach
	void tearDown() {
		mongoTemplate.remove(MongoDbEventPublication.class).all();
	}

	@Test // GH-4
	void shouldPersistAndUpdateEventPublication() {

		var testEvent = new TestEvent("abc");
		var publication = repository.create(EventPublication.of(testEvent, TARGET_IDENTIFIER));

		var eventPublications = repository.findIncompletePublications();

		assertThat(eventPublications).hasSize(1);
		assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
		assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());

		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
				.isPresent();

		// Complete publication
		repository.markCompleted(publication, Instant.now());

		assertThat(repository.findIncompletePublications()).isEmpty();
	}

	@Test // GH-4
	void shouldUpdateSingleEventPublication() {

		var testEvent1 = new TestEvent("id1");
		var testEvent2 = new TestEvent("id2");

		repository.create(EventPublication.of(testEvent1, TARGET_IDENTIFIER));
		var publication = repository.create(EventPublication.of(testEvent2, TARGET_IDENTIFIER));

		repository.markCompleted(publication, Instant.now());

		assertThat(repository.findIncompletePublications()).hasSize(1)
				.element(0).extracting(EventPublication::getEvent).isEqualTo(testEvent1);
	}

	@Test // GH-133
	void returnsOldestIncompletePublicationsFirst() {

		var now = LocalDateTime.now();

		savePublicationAt(now.withHour(3));
		savePublicationAt(now.withHour(0));
		savePublicationAt(now.withHour(1));

		assertThat(repository.findIncompletePublications())
				.isSortedAccordingTo(Comparator.comparing(EventPublication::getPublicationDate));
	}

	private void savePublicationAt(LocalDateTime date) {

		mongoTemplate.save(
				new MongoDbEventPublication(UUID.randomUUID(), date.toInstant(ZoneOffset.UTC), "", "", null));
	}

	@Nested
	class FindByEventAndTargetIdentifier {

		@Test // GH-4
		void shouldFindEventPublicationByEventAndTargetIdentifier() {

			var testEvent1 = new TestEvent("abc");
			var testEvent2 = new TestEvent("def");

			repository.create(EventPublication.of(testEvent2, TARGET_IDENTIFIER));
			repository.create(EventPublication.of(testEvent1, TARGET_IDENTIFIER));
			repository
					.create(EventPublication.of(testEvent1, PublicationTargetIdentifier.of(TARGET_IDENTIFIER.getValue() + "!")));

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent1, TARGET_IDENTIFIER);

			assertThat(actual).hasValueSatisfying(it -> {
				assertThat(it.getEvent()).isEqualTo(testEvent1);
				assertThat(it.getTargetIdentifier()).isEqualTo(TARGET_IDENTIFIER);
			});
		}

		@Test // GH-4
		void shouldTolerateEmptyResultTest() {

			var testEvent = new TestEvent("id");

			assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
					.isEmpty();
		}

		@Test
		void shouldNotReturnCompletedEvents() {

			TestEvent testEvent = new TestEvent("abc");

			EventPublication publication = EventPublication.of(testEvent, TARGET_IDENTIFIER);

			// Store publication
			repository.create(publication);
			repository.markCompleted(publication, Instant.now());

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

			assertThat(actual).isEmpty();
		}

		@Test // GH-4
		void shouldReturnTheOldestEventTest() throws InterruptedException {

			var testEvent = new TestEvent("id");

			var publication = repository.create(EventPublication.of(testEvent, TARGET_IDENTIFIER));
			Thread.sleep(10);
			repository.create(EventPublication.of(testEvent, TARGET_IDENTIFIER));

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

			assertThat(actual).hasValueSatisfying(it -> //
			assertThat(it.getPublicationDate()) //
					.isCloseTo(publication.getPublicationDate(), within(1, ChronoUnit.MILLIS)));
		}
	}

	@Nested
	class DeleteCompletedPublications {

		@Test // GH-20
		void shouldDeleteCompletedEvents() {

			var testEvent1 = new TestEvent("abc");
			var testEvent2 = new TestEvent("def");

			var publication = repository.create(EventPublication.of(testEvent1, TARGET_IDENTIFIER));

			repository.create(EventPublication.of(testEvent2, TARGET_IDENTIFIER));
			repository.markCompleted(publication, Instant.now());
			repository.deleteCompletedPublications();

			assertThat(mongoTemplate.findAll(MongoDbEventPublication.class)) //
					.hasSize(1) //
					.element(0).extracting(it -> it.event).isEqualTo(testEvent2);
		}

		@Test // GH-251
		void shouldDeleteCompletedEventsBefore() {

			var testEvent1 = new TestEvent("abc");
			var testEvent2 = new TestEvent("def");

			var publication1 = repository.create(EventPublication.of(testEvent1, TARGET_IDENTIFIER));
			var publication2 = repository.create(EventPublication.of(testEvent2, TARGET_IDENTIFIER));

			var now = Instant.now();

			repository.markCompleted(publication1, now.minusSeconds(30));
			repository.markCompleted(publication2, now);
			repository.deleteCompletedPublicationsBefore(now.minusSeconds(15));

			assertThat(mongoTemplate.findAll(MongoDbEventPublication.class)) //
					.hasSize(1) //
					.element(0).extracting(it -> it.event).isEqualTo(testEvent2);
		}
	}

	@Value
	private static final class TestEvent {
		String eventId;
	}
}
