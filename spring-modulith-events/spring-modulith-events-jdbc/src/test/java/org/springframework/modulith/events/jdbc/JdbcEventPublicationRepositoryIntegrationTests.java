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
package org.springframework.modulith.events.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.Value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.temporal.ChronoUnit;

/**
 * Integration tests for {@link JdbcEventPublicationRepository}.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
class JdbcEventPublicationRepositoryIntegrationTests {

	static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");

	@JdbcTest
	@Import(TestApplication.class)
	@ContextConfiguration(classes = JdbcEventPublicationAutoConfiguration.class)
	abstract class TestBase {

		@Autowired JdbcOperations operations;
		@Autowired JdbcEventPublicationRepository repository;

		@MockBean EventSerializer serializer;

		@BeforeEach
		void cleanUp() {
			operations.execute("TRUNCATE TABLE EVENT_PUBLICATION");
		}

		@Test // GH-3
		void shouldPersistAndUpdateEventPublication() {

			var testEvent = new TestEvent("id");
			var serializedEvent = "{\"eventId\":\"id\"}";

			when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
			when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

			var publication = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

			// Store publication
			repository.create(publication);

			var eventPublications = repository.findIncompletePublications();

			assertThat(eventPublications).hasSize(1);
			assertThat(eventPublications).element(0).satisfies(it -> {
				assertThat(it.getEvent()).isEqualTo(publication.getEvent());
				assertThat(it.getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());
			});

			assertThat(repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
					.isPresent();

			// Complete publication
			repository.update(publication.markCompleted());

			assertThat(repository.findIncompletePublications()).isEmpty();
		}

		@Nested
		class Update {

			@Test
				// GH-3
			void shouldUpdateSingleEventPublication() {

				var testEvent1 = new TestEvent("id1");
				var testEvent2 = new TestEvent("id2");
				var serializedEvent1 = "{\"eventId\":\"id1\"}";
				var serializedEvent2 = "{\"eventId\":\"id2\"}";

				when(serializer.serialize(testEvent1)).thenReturn(serializedEvent1);
				when(serializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
				when(serializer.serialize(testEvent2)).thenReturn(serializedEvent2);
				when(serializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

				var publication1 = CompletableEventPublication.of(testEvent1, TARGET_IDENTIFIER);
				var publication2 = CompletableEventPublication.of(testEvent2, TARGET_IDENTIFIER);

				// Store publication
				repository.create(publication1);
				repository.create(publication2);

				// Complete publication
				repository.update(publication2.markCompleted());

				assertThat(repository.findIncompletePublications()).hasSize(1)
						.element(0).extracting(EventPublication::getEvent).isEqualTo(testEvent1);
			}
		}

		@Nested
		class FindByEventAndTargetIdentifier {

			@Test
				// GH-3
			void shouldTolerateEmptyResult() {

				var testEvent = new TestEvent("id");
				var serializedEvent = "{\"eventId\":\"id\"}";

				when(serializer.serialize(testEvent)).thenReturn(serializedEvent);

				assertThat(repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER)).isEmpty();
			}

			@Test
				// GH-3
			void shouldReturnTheOldestEvent() throws Exception {

				var testEvent = new TestEvent("id");
				var serializedEvent = "{\"eventId\":\"id\"}";

				when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
				when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

				var publicationOld = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);
				Thread.sleep(10);
				var publicationNew = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

				repository.create(publicationNew);
				repository.create(publicationOld);

				var actual = repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

				assertThat(actual).hasValueSatisfying(it -> {
					assertThat(it.getPublicationDate()) //
						.isCloseTo(publicationOld.getPublicationDate(), within(1, ChronoUnit.MILLIS));
				});
			}

			@Test
				// GH-3
			void shouldSilentlyIgnoreNotSerializableEvents() {

				var testEvent = new TestEvent("id");
				var serializedEvent = "{\"eventId\":\"id\"}";

				when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
				when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

				// Store publication
				repository.create(CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER));

				operations.update("UPDATE EVENT_PUBLICATION SET EVENT_TYPE='abc'");

				assertThat(repository.findByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER)).isEmpty();
			}
		}
	}

	@Nested
	@ActiveProfiles("hsqldb")
	class HSQL extends TestBase {}

	@Nested
	@ActiveProfiles("h2")
	class H2 extends TestBase {}

	@Nested
	@ActiveProfiles("postgres")
	class Postgres extends TestBase {}

	@Value
	private static final class TestEvent {
		String eventId;
	}
}
