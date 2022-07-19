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
package org.springframework.modulith.testapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.modulith.events.jdbc.DatabaseSchemaInitializer;
import org.springframework.modulith.events.jdbc.JdbcEventPublicationRepository;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author Dmitry Belyaev, Björn Kieling
 */
class JdbcEventPublicationRepositoryIntegrationTests {

	private static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");

	private JdbcEventPublicationRepository repository;

	private final EventSerializer eventSerializer = mock(EventSerializer.class);

	private abstract class TestBase {

		@Autowired
		protected JdbcTemplate jdbcTemplate;

		@BeforeEach
		public void setUp() {
			repository = new JdbcEventPublicationRepository(jdbcTemplate, eventSerializer);
		}
	}

	@Nested
	@DataJdbcTest
	@ActiveProfiles("hsql")
	@Import(DatabaseSchemaInitializer.class)
	class HSQL extends TestBase {

		@Nested
		class CreateAndUpdate {

			@Test
			void shouldPersistAndUpdateEventPublication() {
				shouldPersistAndUpdateEventPublicationTest();
			}

			@Test
			void shouldUpdateSingleEventPublication() {
				shouldUpdateSingleEventPublicationTest();
			}
		}

		@Nested
		class FindByCompletionDateIsNull {

			@Test
			void shouldSilentlyIgnoreNotSerializableEvents() {
				shouldSilentlyIgnoreNotSerializableEventsTest(jdbcTemplate);
			}
		}

		@Nested
		class FindBySerializedEventAndListenerId {

			@Test
			void shouldTolerateEmptyResult() {
				shouldTolerateEmptyResultTest();
			}

			@Test
			void shouldReturnTheOldestEvent() throws InterruptedException {
				shouldReturnTheOldestEventTest();
			}

			@Test
			void shouldSilentlyIgnoreNotSerializableEvents() {
				shouldSilentlyIgnoreNotSerializableEventsTest(jdbcTemplate);
			}
		}
	}

	@Nested
	@DataJdbcTest
	@ActiveProfiles("h2")
	@Import(DatabaseSchemaInitializer.class)
	class H2 extends TestBase {

		@Nested
		class CreateAndUpdate {

			@Test
			void shouldPersistAndUpdateEventPublication() {
				shouldPersistAndUpdateEventPublicationTest();
			}

			@Test
			void shouldUpdateSingleEventPublication() {
				shouldUpdateSingleEventPublicationTest();
			}
		}

		@Nested
		class FindByCompletionDateIsNull {

			@Test
			void shouldSilentlyIgnoreNotSerializableEvents() {
				shouldSilentlyIgnoreNotSerializableEventsTest(jdbcTemplate);
			}
		}

		@Nested
		class FindBySerializedEventAndListenerId {

			@Test
			void shouldTolerateEmptyResult() {
				shouldTolerateEmptyResultTest();
			}

			@Test
			void shouldReturnTheOldestEvent() throws InterruptedException {
				shouldReturnTheOldestEventTest();
			}

			@Test
			void shouldSilentlyIgnoreNotSerializableEvents() {
				shouldSilentlyIgnoreNotSerializableEventsTest(jdbcTemplate);
			}
		}
	}

	@Nested
	@Disabled
	@DataJdbcTest
	@ActiveProfiles("postgres")
	@Import(DatabaseSchemaInitializer.class)
	class Postgres extends TestBase {

		@Nested
		class CreateAndUpdate {

			@Test
			void shouldPersistAndUpdateEventPublication() {
				shouldPersistAndUpdateEventPublicationTest();
			}

			@Test
			void shouldUpdateSingleEventPublication() {
				shouldUpdateSingleEventPublicationTest();
			}
		}

		@Nested
		class FindByCompletionDateIsNull {

			@Test
			void shouldSilentlyIgnoreNotSerializableEvents() {
				shouldSilentlyIgnoreNotSerializableEventsTest(jdbcTemplate);
			}
		}

		@Nested
		class FindBySerializedEventAndListenerId {

			@Test
			void shouldTolerateEmptyResult() {
				shouldTolerateEmptyResultTest();
			}

			@Test
			void shouldReturnTheOldestEvent() throws InterruptedException {
				shouldReturnTheOldestEventTest();
			}

			@Test
			void shouldSilentlyIgnoreNotSerializableEvents() {
				shouldSilentlyIgnoreNotSerializableEventsTest(jdbcTemplate);
			}
		}
	}

	private void shouldPersistAndUpdateEventPublicationTest() {
		TestEvent testEvent = new TestEvent("id");
		String serializedEvent = "{\"eventId\":\"id\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		CompletableEventPublication publication = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

		// Store publication
		repository.create(publication);

		List<EventPublication> eventPublications = repository.findByCompletionDateIsNull();
		assertThat(eventPublications).hasSize(1);
		assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
		assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());
		assertThat(repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
				.isPresent();

		// Complete publication
		repository.updateCompletionDate(publication.markCompleted());

		assertThat(repository.findByCompletionDateIsNull()).isEmpty();
	}

	private void shouldUpdateSingleEventPublicationTest() {
		TestEvent testEvent1 = new TestEvent("id1");
		TestEvent testEvent2 = new TestEvent("id2");
		String serializedEvent1 = "{\"eventId\":\"id1\"}";
		String serializedEvent2 = "{\"eventId\":\"id2\"}";

		when(eventSerializer.serialize(testEvent1)).thenReturn(serializedEvent1);
		when(eventSerializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
		when(eventSerializer.serialize(testEvent2)).thenReturn(serializedEvent2);
		when(eventSerializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

		CompletableEventPublication publication1 = CompletableEventPublication.of(testEvent1, TARGET_IDENTIFIER);
		CompletableEventPublication publication2 = CompletableEventPublication.of(testEvent2, TARGET_IDENTIFIER);

		// Store publication
		repository.create(publication1);
		repository.create(publication2);

		// Complete publication
		repository.updateCompletionDate(publication2.markCompleted());

		List<EventPublication> withCompletionDateNull = repository.findByCompletionDateIsNull();
		assertThat(withCompletionDateNull).hasSize(1);
		assertThat(withCompletionDateNull.get(0).getEvent()).isEqualTo(testEvent1);
	}

	private void shouldTolerateEmptyResultTest() {
		TestEvent testEvent = new TestEvent("id");
		String serializedEvent = "{\"eventId\":\"id\"}";
		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);

		Optional<EventPublication> actual =
				repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

		assertThat(actual).isEmpty();
	}

	private void shouldReturnTheOldestEventTest() throws InterruptedException {
		TestEvent testEvent = new TestEvent("id");
		String serializedEvent = "{\"eventId\":\"id\"}";
		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		CompletableEventPublication publicationOld = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);
		Thread.sleep(10);
		CompletableEventPublication publicationNew = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

		repository.create(publicationNew);
		repository.create(publicationOld);


		Optional<EventPublication> actual =
				repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

		assertThat(actual).isNotEmpty();
		assertThat(actual.get().getPublicationDate()).isEqualTo(publicationOld.getPublicationDate());
	}

	private void shouldSilentlyIgnoreNotSerializableEventsTest(JdbcTemplate jdbcTemplate) {
		TestEvent testEvent = new TestEvent("id");
		String serializedEvent = "{\"eventId\":\"id\"}";
		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		CompletableEventPublication publication = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

		// Store publication
		repository.create(publication);
		jdbcTemplate.update("UPDATE EVENT_PUBLICATION SET EVENT_TYPE='abc'");

		Optional<EventPublication> actual =
				repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

		assertThat(actual).isEmpty();
	}

	private static final class TestEvent {
		private final String eventId;

		private TestEvent(String eventId) {
			this.eventId = eventId;
		}
	}
}
