/*
 * Copyright 2022-2024 the original author or authors.
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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Björn Kieling
 * @author Dmitry Belyaev
 * @author Oliver Drotbohm
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

		var publication = createPublication(new TestEvent("abc"));

		var eventPublications = repository.findIncompletePublications();

		assertThat(eventPublications).hasSize(1);
		assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
		assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());

		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(new TestEvent("abc"), TARGET_IDENTIFIER))
				.isPresent();

		// Complete publication
		repository.markCompleted(publication, Instant.now());

		assertThat(repository.findIncompletePublications()).isEmpty();
	}

	@Test // GH-4
	void shouldUpdateSingleEventPublication() {

		var first = createPublication(new TestEvent("id1"));
		var second = createPublication(new TestEvent("id2"));

		repository.markCompleted(second, Instant.now());

		assertThat(repository.findIncompletePublications()).hasSize(1)
				.element(0)
				.extracting(TargetEventPublication::getEvent).isEqualTo(first.getEvent());
	}

	@Test // GH-133
	void returnsOldestIncompletePublicationsFirst() {

		var now = LocalDateTime.now();

		savePublicationAt(now.withHour(3));
		savePublicationAt(now.withHour(0));
		savePublicationAt(now.withHour(1));

		assertThat(repository.findIncompletePublications())
				.isSortedAccordingTo(Comparator.comparing(TargetEventPublication::getPublicationDate));
	}

	@Test // GH-294
	void findsPublicationsOlderThanReference() throws Exception {

		var first = createPublication(new TestEvent("first"));

		Thread.sleep(100);

		var now = Instant.now();
		var second = createPublication(new TestEvent("second"));

		assertThat(repository.findIncompletePublications())
				.extracting(TargetEventPublication::getIdentifier)
				.containsExactly(first.getIdentifier(), second.getIdentifier());

		assertThat(repository.findIncompletePublicationsPublishedBefore(now))
				.hasSize(1)
				.element(0).extracting(TargetEventPublication::getIdentifier).isEqualTo(first.getIdentifier());
	}

	@Test // GH-452
	void findsCompletedPublications() {

		var event = new TestEvent("first");
		var publication = createPublication(event);

		repository.markCompleted(publication, Instant.now());

		assertThat(repository.findCompletedPublications())
				.hasSize(1)
				.element(0)
				.extracting(TargetEventPublication::getEvent)
				.isEqualTo(event);
	}

	private TargetEventPublication createPublication(Object event) {
		return createPublication(event, TARGET_IDENTIFIER);
	}

	private TargetEventPublication createPublication(Object event, PublicationTargetIdentifier id) {
		return repository.create(TargetEventPublication.of(event, id));
	}

	private void savePublicationAt(LocalDateTime date) {

		mongoTemplate.save(
				new MongoDbEventPublication(UUID.randomUUID(), date.toInstant(ZoneOffset.UTC), "", "", null));
	}

	@Nested
	class FindByEventAndTargetIdentifier {

		@Test // GH-4
		void shouldFindEventPublicationByEventAndTargetIdentifier() {

			var first = createPublication(new TestEvent("abc"));
			createPublication(new TestEvent("def"));

			var firstEvent = first.getEvent();

			createPublication(firstEvent, PublicationTargetIdentifier.of("somethingDifferen"));

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(firstEvent, TARGET_IDENTIFIER);

			assertThat(actual).hasValueSatisfying(it -> {
				assertThat(it.getEvent()).isEqualTo(firstEvent);
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

			var publication = createPublication(new TestEvent("abc"));

			repository.markCompleted(publication, Instant.now());

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(publication.getEvent(),
					TARGET_IDENTIFIER);

			assertThat(actual).isEmpty();
		}

		@Test // GH-4
		void shouldReturnTheOldestEventTest() throws InterruptedException {

			var publication = createPublication(new TestEvent("id"));

			Thread.sleep(10);
			repository.create(publication);

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(publication.getEvent(),
					TARGET_IDENTIFIER);

			assertThat(actual).hasValueSatisfying(it -> //
			assertThat(it.getPublicationDate()) //
					.isCloseTo(publication.getPublicationDate(), within(1, ChronoUnit.MILLIS)));
		}
	}

	@Nested
	class DeleteCompletedPublications {

		@Test // GH-20
		void shouldDeleteCompletedEvents() {

			var publication = createPublication(new TestEvent("abc"));
			var second = createPublication(new TestEvent("def"));

			repository.markCompleted(publication, Instant.now());
			repository.deleteCompletedPublications();

			assertThat(mongoTemplate.findAll(MongoDbEventPublication.class)) //
					.hasSize(1) //
					.element(0) //
					.extracting(it -> it.event) //
					.isEqualTo(second.getEvent());
		}

		@Test // GH-251
		void shouldDeleteCompletedEventsBefore() {

			var first = createPublication(new TestEvent("abc"));
			var second = createPublication(new TestEvent("def"));

			var now = Instant.now();

			repository.markCompleted(first, now.minusSeconds(30));
			repository.markCompleted(second, now);
			repository.deleteCompletedPublicationsBefore(now.minusSeconds(15));

			assertThat(mongoTemplate.findAll(MongoDbEventPublication.class)) //
					.hasSize(1) //
					.element(0).extracting(it -> it.event).isEqualTo(second.getEvent());
		}

		@Test // GH-294
		void deletesPublicationsByIdentifier() {

			var first = createPublication(new TestEvent("first"));
			var second = createPublication(new TestEvent("second"));

			repository.deletePublications(List.of(first.getIdentifier()));

			assertThat(repository.findIncompletePublications())
					.hasSize(1)
					.element(0)
					.matches(it -> it.getIdentifier().equals(second.getIdentifier()))
					.matches(it -> it.getEvent().equals(second.getEvent()));
		}
	}

	@Value
	private static final class TestEvent {
		String eventId;
	}
}
