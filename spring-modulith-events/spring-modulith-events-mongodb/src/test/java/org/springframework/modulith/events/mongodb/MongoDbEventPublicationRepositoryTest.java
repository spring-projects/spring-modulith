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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.modulith.testapp.Infrastructure;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @author Björn Kieling
 * @author Dmitry Belyaev
 * @author Oliver Drotbohm
 */
@Testcontainers(disabledWithoutDocker = true)
class MongoDbEventPublicationRepositoryTest {

	private static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");

	@DataMongoTest
	@Import(Infrastructure.class)
	@ContextConfiguration(classes = TestApplication.class)
	static abstract class TestBase {

		@Autowired MongoTemplate mongoTemplate;
		@Autowired Environment environment;

		MongoDbEventPublicationRepository repository;
		CompletionMode completionMode;
		String archiveCollection = MongoDbEventPublicationRepository.ARCHIVE_COLLECTION;

		@BeforeEach
		void setUp() {
			this.completionMode = CompletionMode.from(environment);
			this.repository = new MongoDbEventPublicationRepository(mongoTemplate, completionMode);
		}

		@AfterEach
		void tearDown() {
			mongoTemplate.remove(MongoDbEventPublication.class).all();
			mongoTemplate.remove(MongoDbEventPublication.class).inCollection(archiveCollection).all();
		}

		@Test // GH-4
		void shouldPersistAndUpdateEventPublication() {

			var publication = createPublication(new TestEvent("abc"));

			var eventPublications = repository.findIncompletePublications();

			assertThat(eventPublications).hasSize(1);
			assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
			assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());

			assertThat(
					repository.findIncompletePublicationsByEventAndTargetIdentifier(new TestEvent("abc"), TARGET_IDENTIFIER))
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

		@Test // GH-451
		void findsCompletedPublications() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markCompleted(publication, Instant.now());

			if (completionMode == CompletionMode.DELETE) {

				assertThat(repository.findCompletedPublications()).isEmpty();

			} else {

				assertThat(repository.findCompletedPublications())
						.hasSize(1)
						.element(0)
						.extracting(TargetEventPublication::getEvent)
						.isEqualTo(event);
			}

		}

		@Test // GH-258
		void marksPublicationAsCompletedById() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markCompleted(publication.getIdentifier(), Instant.now());

			assertThat(repository.findIncompletePublications()).isEmpty();

			if (completionMode == CompletionMode.DELETE) {

				assertThat(repository.findCompletedPublications()).isEmpty();

			} else {

				assertThat(repository.findCompletedPublications())
						.extracting(TargetEventPublication::getIdentifier)
						.containsExactly(publication.getIdentifier());
			}

			if (completionMode == CompletionMode.ARCHIVE) {
				assertThat(mongoTemplate.findAll(MongoDbEventPublication.class, archiveCollection)).isNotEmpty();
			}
		}

		@Test // GH-4
		void shouldFindEventPublicationByEventAndTargetIdentifier() {

			var first = createPublication(new TestEvent("abc"));
			createPublication(new TestEvent("def"));

			var firstEvent = first.getEvent();

			createPublication(firstEvent, PublicationTargetIdentifier.of("somethingDifferent"));

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

			assumeTrue(completionMode == CompletionMode.UPDATE);

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

		@Test // GH-1336
		void looksUpFailedPublication() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markFailed(publication.getIdentifier());

			assertThat(repository.findFailedPublications(FailedCriteria.ALL))
					.extracting(TargetEventPublication::getIdentifier)
					.containsExactly(publication.getIdentifier());
		}

		@Test // GH-1336
		void claimsResubmissionOnce() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markFailed(publication.getIdentifier());

			var now = Instant.now();

			assertThat(repository.markResubmitted(publication.getIdentifier(), now)).isTrue();
			assertThat(repository.markResubmitted(publication.getIdentifier(), now)).isFalse();
		}

		@Test // GH-1336, GH-1855
		void countsByStatus() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			assertByStatus(Status.PUBLISHED, publication.getIdentifier());

			repository.markFailed(publication.getIdentifier());
			assertByStatus(Status.FAILED, publication.getIdentifier());

			repository.markResubmitted(publication.getIdentifier(), Instant.now());
			assertByStatus(Status.RESUBMITTED, publication.getIdentifier());

			repository.markCompleted(publication.getIdentifier(), Instant.now());
			assertCompleted(publication.getIdentifier());
		}

		@Test // GH-1855
		void marksPublicationAsCompletedByEventAndTargetIdentifier() {

			var publication = createPublication(new TestEvent("first"));

			repository.markCompleted(publication.getEvent(), TARGET_IDENTIFIER, Instant.now());

			assertCompleted(publication.getIdentifier());
		}

		@ParameterizedTest // GH-1855
		@EnumSource(Status.class)
		void findsPublicationsByStatusInPublicationOrder(Status status) {

			var now = Instant.parse("2026-01-01T12:00:00Z");
			var second = savePublicationAt(now, status);
			var first = savePublicationAt(now.minusSeconds(1), status);
			savePublicationAt(now.minusSeconds(2), status == Status.FAILED ? Status.PUBLISHED : Status.FAILED);

			assertByStatusResult(status, first.id, second.id);
		}

		@ParameterizedTest // GH-1855
		@EnumSource(value = Status.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
		void recognizesPreviouslyCompletedPublications(Status status) {

			var now = Instant.parse("2026-01-01T12:00:00Z");
			var collection = completionMode == CompletionMode.ARCHIVE
					? archiveCollection
					: mongoTemplate.getCollectionName(MongoDbEventPublication.class);

			// Older versions recorded completion without updating the stored status.
			var publication = new MongoDbEventPublication(UUID.randomUUID(), now.minusSeconds(60), "listener",
					new TestEvent("completed"), now, status, null, 1);

			mongoTemplate.save(publication, collection);

			assertByStatus(Status.COMPLETED, publication.id);
			assertThat(repository.findCompletedPublications()).singleElement()
					.satisfies(it -> assertThat(it.getStatus()).isEqualTo(Status.COMPLETED));
			assertThat(repository.findFailedPublications(FailedCriteria.ALL)).isEmpty();

			repository.markFailed(publication.id);
			assertThat(repository.markResubmitted(publication.id, now.plusSeconds(1))).isFalse();

			assertThat(mongoTemplate.findAll(MongoDbEventPublication.class, collection)).singleElement()
					.satisfies(it -> assertThat(it.status).isEqualTo(status));
		}

		@Test // GH-1855
		void doesNotFailPublicationCompletedAfterStatusLookup() {

			var publication = createPublication(new TestEvent("first"));
			var candidates = repository.findByStatus(Status.PUBLISHED);

			assertThat(candidates).hasSize(1);

			repository.markCompleted(publication.getIdentifier(), Instant.now());
			candidates.forEach(it -> repository.markFailed(it.getIdentifier()));

			assertCompleted(publication.getIdentifier());
			assertThat(repository.findFailedPublications(FailedCriteria.ALL)).isEmpty();
		}

		@Test // GH-1855
		void doesNotResubmitPublicationCompletedAfterFailedLookup() {

			var publication = createPublication(new TestEvent("first"));
			repository.markFailed(publication.getIdentifier());

			var candidates = repository.findFailedPublications(FailedCriteria.ALL);

			assertThat(candidates).hasSize(1);

			repository.markCompleted(publication.getIdentifier(), Instant.now());
			candidates.forEach(it -> assertThat(repository.markResubmitted(it.getIdentifier(), Instant.now())).isFalse());

			assertCompleted(publication.getIdentifier());
		}

		@Test // GH-1855
		void resubmitsOnlyStaleIncompletePublications() {

			var now = Instant.parse("2026-01-01T12:00:00Z");
			var old = now.minusSeconds(120);
			var published = savePublicationAt(old, Status.PUBLISHED);
			var processing = savePublicationAt(old.plusSeconds(1), Status.PROCESSING);
			var resubmitted = savePublicationAt(old.plusSeconds(2), Status.PUBLISHED);
			var recent = savePublicationAt(old.plusSeconds(3), Status.PUBLISHED);
			var completed = savePublicationAt(old.plusSeconds(4), Status.PUBLISHED);

			assertThat(repository.markResubmitted(resubmitted.id, old.plusSeconds(30))).isTrue();
			assertThat(repository.markResubmitted(recent.id, now.minusSeconds(5))).isTrue();
			repository.markCompleted(completed.id, now.minusSeconds(30));

			var registry = new DefaultEventPublicationRegistry(repository, Clock.fixed(now, ZoneOffset.UTC));

			registry.markStalePublicationsFailed(__ -> Duration.ofSeconds(60));

			assertByStatusResult(Status.FAILED, published.id, processing.id, resubmitted.id);
			assertByStatusResult(Status.RESUBMITTED, recent.id);

			var resubmittedIdentifiers = new ArrayList<UUID>();

			registry.processFailedPublications(ResubmissionOptions.defaults(),
					it -> resubmittedIdentifiers.add(it.getIdentifier()));

			assertThat(resubmittedIdentifiers).containsExactly(published.id, processing.id, resubmitted.id);
			assertByStatusResult(Status.FAILED);
			assertByStatusResult(Status.RESUBMITTED, published.id, processing.id, resubmitted.id, recent.id);
			assertByStatusResult(Status.COMPLETED,
					completionMode == CompletionMode.DELETE ? new UUID[0] : new UUID[] { completed.id });
		}

		@Test // GH-1336
		void marksPublicationAsProcessing() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markProcessing(publication.getIdentifier());
		}

		@Test // GH-1336
		void looksUpFailedPublicationInBatch() {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markFailed(publication.getIdentifier());

			assertThat(repository.findFailedPublications(FailedCriteria.ALL.withItemsToRead(10)))
					.extracting(TargetEventPublication::getIdentifier)
					.containsExactly(publication.getIdentifier());
		}

		@Test // GH-1321
		void looksUpFailedPublicationWithReferenceDate() throws Exception {

			var event = new TestEvent("first");
			var publication = createPublication(event);

			repository.markFailed(publication.getIdentifier());

			Thread.sleep(200);

			var criteria = FailedCriteria.ALL
					.withPublicationsPublishedBefore(publication.getPublicationDate().plusMillis(50));

			assertThat(repository.findFailedPublications(criteria))
					.extracting(TargetEventPublication::getIdentifier)
					.containsExactly(publication.getIdentifier());
		}

		private TargetEventPublication createPublication(Object event) {
			return createPublication(event, TARGET_IDENTIFIER);
		}

		private TargetEventPublication createPublication(Object event, PublicationTargetIdentifier id) {
			return repository.create(TargetEventPublication.of(event, id));
		}

		private void savePublicationAt(LocalDateTime date) {

			var now = date.toInstant(ZoneOffset.UTC);
			var publication = new MongoDbEventPublication(UUID.randomUUID(), now, "", "", null, Status.PUBLISHED, now, 1);

			mongoTemplate.save(publication);
		}

		private MongoDbEventPublication savePublicationAt(Instant date, Status status) {

			var completed = status == Status.COMPLETED;
			var publication = new MongoDbEventPublication(UUID.randomUUID(), date, "listener", new TestEvent("event"),
					completed ? date.plusSeconds(1) : null, status, null, 1);

			return completed && completionMode == CompletionMode.ARCHIVE
					? mongoTemplate.save(publication, archiveCollection)
					: mongoTemplate.save(publication);
		}

		private void assertCompleted(UUID identifier) {

			if (completionMode == CompletionMode.DELETE) {

				assertByStatus(Status.COMPLETED);
				assertThat(mongoTemplate.findAll(MongoDbEventPublication.class)).isEmpty();

			} else {

				assertByStatus(Status.COMPLETED, identifier);

				var collection = completionMode == CompletionMode.ARCHIVE
						? archiveCollection
						: mongoTemplate.getCollectionName(MongoDbEventPublication.class);

				assertThat(mongoTemplate.findAll(MongoDbEventPublication.class, collection)).singleElement().satisfies(it -> {
					assertThat(it.id).isEqualTo(identifier);
					assertThat(it.status).isEqualTo(Status.COMPLETED);
					assertThat(it.completionDate).isNotNull();
				});
			}
		}

		private void assertByStatus(Status reference, UUID... identifiers) {

			for (var status : Status.values()) {
				assertByStatusResult(status, status == reference ? identifiers : new UUID[0]);
			}
		}

		private void assertByStatusResult(Status status, UUID... identifiers) {

			assertThat(repository.countByStatus(status)).isEqualTo(identifiers.length);
			assertThat(repository.findByStatus(status))
					.extracting(TargetEventPublication::getIdentifier)
					.containsExactly(identifiers);
			assertThat(repository.findByStatus(status))
					.allSatisfy(it -> assertThat(it.getStatus()).isEqualTo(status));
		}
	}

	@Nested
	class WithUpdateCompletionTest extends TestBase {}

	@Nested
	@TestPropertySource(properties = CompletionMode.PROPERTY + "=DELETE")
	class WithDeleteCompletionTest extends TestBase {}

	@Nested
	@TestPropertySource(properties = CompletionMode.PROPERTY + "=ARCHIVE")
	class WithArchiveCompletionTest extends TestBase {}

	private record TestEvent(String eventId) {}
}
