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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ContextConfiguration;

import com.mongodb.client.MongoClients;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import lombok.EqualsAndHashCode;

/**
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
@DataMongoTest
@ContextConfiguration(classes = TestApplication.class)
class MongoDbEventPublicationRepositoryTest {

	private static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");
	private static final String CONNECTION_STRING = "mongodb://%s:%d";

	private static String ip;
	private static int port;
	private static MongodExecutable mongodExecutable;

	private MongoTemplate mongoTemplate;

	private MongoDbEventPublicationRepository repository;

	@BeforeAll
	static void startupMongoDb() throws IOException {

		// Refer to https://www.baeldung.com/spring-boot-embedded-mongodb
		ip = "localhost";
		port = Network.freeServerPort(Network.getLocalHost());

		ImmutableMongodConfig mongodConfig = MongodConfig.builder().version(Version.Main.PRODUCTION)
				.net(new Net(ip, port, Network.localhostIsIPv6())).build();

		MongodStarter starter = MongodStarter.getDefaultInstance();
		mongodExecutable = starter.prepare(mongodConfig);
		mongodExecutable.start();
	}

	@AfterAll
	static void shutdownMongoDb() {
		mongodExecutable.stop();
	}

	@BeforeEach
	void setUp() {
		mongoTemplate = new MongoTemplate(MongoClients.create(String.format(CONNECTION_STRING, ip, port)), "test");

		repository = new MongoDbEventPublicationRepository(mongoTemplate);
	}

	@AfterEach
	void tearDown() {
		mongoTemplate.remove(MongoDbEventPublication.class).all();
	}

	@Test
	void shouldPersistAndUpdateEventPublication() {

		TestEvent testEvent = new TestEvent("abc");

		CompletableEventPublication publication = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

		// Store publication
		repository.create(publication);

		List<EventPublication> eventPublications = repository.findIncompletePublications();
		assertThat(eventPublications).hasSize(1);
		assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
		assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());

		assertThat(repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER)).isPresent();

		// Complete publication
		repository.update(publication.markCompleted());

		assertThat(repository.findIncompletePublications()).isEmpty();
	}

	@Test
	void shouldUpdateSingleEventPublication() {
		TestEvent testEvent1 = new TestEvent("id1");
		TestEvent testEvent2 = new TestEvent("id2");

		CompletableEventPublication publication1 = CompletableEventPublication.of(testEvent1, TARGET_IDENTIFIER);
		CompletableEventPublication publication2 = CompletableEventPublication.of(testEvent2, TARGET_IDENTIFIER);

		repository.create(publication1);
		repository.create(publication2);

		repository.update(publication2.markCompleted());

		List<EventPublication> withCompletionDateNull = repository.findIncompletePublications();
		assertThat(withCompletionDateNull).hasSize(1);
		assertThat(withCompletionDateNull.get(0).getEvent()).isEqualTo(testEvent1);
	}

	@Nested
	class FindByEventAndTargetIdentifier {
		@Test
		void shouldFindEventPublicationByEventAndTargetIdentifier() {
			TestEvent testEvent1 = new TestEvent("abc");
			TestEvent testEvent2 = new TestEvent("def");

			CompletableEventPublication publication2 = CompletableEventPublication.of(testEvent2, TARGET_IDENTIFIER);
			repository.create(publication2);

			CompletableEventPublication publication1 = CompletableEventPublication.of(testEvent1, TARGET_IDENTIFIER);
			repository.create(publication1);

			CompletableEventPublication publication3 = CompletableEventPublication.of(
					testEvent1, PublicationTargetIdentifier.of(TARGET_IDENTIFIER.getValue() + "!"));
			repository.create(publication3);

			Optional<EventPublication> actual = repository.findByEventAndTargetIdentifier(testEvent1, TARGET_IDENTIFIER);
			assertThat(actual).isPresent();
			assertThat(actual.get().getEvent()).isEqualTo(testEvent1);
			assertThat(actual.get().getTargetIdentifier()).isEqualTo(TARGET_IDENTIFIER);
		}

		@Test
		void shouldTolerateEmptyResultTest() {
			TestEvent testEvent = new TestEvent("id");

			Optional<EventPublication> actual =
					repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

			assertThat(actual).isEmpty();
		}

		@Test
		void shouldReturnTheOldestEventTest() throws InterruptedException {
			TestEvent testEvent = new TestEvent("id");

			CompletableEventPublication publicationOld = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);
			Thread.sleep(10);
			CompletableEventPublication publicationNew = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

			repository.create(publicationNew);
			repository.create(publicationOld);

			Optional<EventPublication> actual =
					repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

			assertThat(actual).isNotEmpty();
			assertThat(actual.get().getPublicationDate())
					.isCloseTo(publicationOld.getPublicationDate(), within(1, ChronoUnit.MILLIS));
		}
	}

	@EqualsAndHashCode
	@Getter
	private static final class TestEvent {
		private final String eventId;

		private TestEvent(String eventId) {
			this.eventId = eventId;
		}
	}
}
