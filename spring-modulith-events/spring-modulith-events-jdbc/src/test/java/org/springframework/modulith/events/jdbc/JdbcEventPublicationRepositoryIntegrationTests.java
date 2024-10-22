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
package org.springframework.modulith.events.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link JdbcEventPublicationRepository}.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 * @author Raed Ben Hamouda
 * @author Cora Iberkleid
 */
class JdbcEventPublicationRepositoryIntegrationTests {

	static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");

	@Import(TestApplication.class)
	@Testcontainers(disabledWithoutDocker = true)
	@ContextConfiguration(classes = JdbcEventPublicationAutoConfiguration.class)
	static abstract class TestBase {

		@Autowired JdbcOperations operations;
		@Autowired JdbcEventPublicationRepository repository;
		@Autowired JdbcRepositorySettings properties;

		@MockitoBean EventSerializer serializer;

		@AfterEach
		@BeforeEach
		void cleanUp() {

			operations.execute("TRUNCATE TABLE " + table());

			if (properties.isArchiveCompletion()) {
				operations.execute("TRUNCATE TABLE " + archiveTable());
			}
		}

		@Test // GH-3
		void shouldPersistAndUpdateEventPublication() {

			var testEvent = new TestEvent("id");
			var serializedEvent = "{\"eventId\":\"id\"}";

			when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
			when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

			var publication = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

			var eventPublications = repository.findIncompletePublications();

			assertThat(eventPublications).hasSize(1);
			assertThat(eventPublications).element(0).satisfies(it -> {
				assertThat(it.getEvent()).isEqualTo(publication.getEvent());
				assertThat(it.getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());
			});

			assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
					.isPresent();

			// Complete publication
			repository.markCompleted(publication, Instant.now());

			assertThat(repository.findIncompletePublications()).isEmpty();
		}

		@Test // GH-133
		void returnsOldestIncompletePublicationsFirst() {

			when(serializer.serialize(any())).thenReturn("{}");

			var now = LocalDateTime.now();

			createPublicationAt(now.withHour(3));
			createPublicationAt(now.withHour(0));
			createPublicationAt(now.withHour(1));

			assertThat(repository.findIncompletePublications())
					.isSortedAccordingTo(Comparator.comparing(TargetEventPublication::getPublicationDate));
		}

		private void createPublicationAt(LocalDateTime publicationDate) {
			repository.create(TargetEventPublication.of("", TARGET_IDENTIFIER, publicationDate.toInstant(ZoneOffset.UTC)));
		}

		@Test // GH-3
		void shouldUpdateSingleEventPublication() {

			var testEvent1 = new TestEvent("id1");
			var testEvent2 = new TestEvent("id2");
			var serializedEvent1 = "{\"eventId\":\"id1\"}";
			var serializedEvent2 = "{\"eventId\":\"id2\"}";

			when(serializer.serialize(testEvent1)).thenReturn(serializedEvent1);
			when(serializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
			when(serializer.serialize(testEvent2)).thenReturn(serializedEvent2);
			when(serializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

			repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));
			var publication = repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));

			// Complete publication
			repository.markCompleted(publication, Instant.now());

			assertThat(repository.findIncompletePublications()).hasSize(1)
					.element(0).extracting(TargetEventPublication::getEvent).isEqualTo(testEvent1);
		}

		@Test // GH-3
		void shouldTolerateEmptyResult() {

			var testEvent = new TestEvent("id");
			var serializedEvent = "{\"eventId\":\"id\"}";

			when(serializer.serialize(testEvent)).thenReturn(serializedEvent);

			assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
					.isEmpty();
		}

		@Test // GH-3
		void shouldNotReturnCompletedEvents() {

			var testEvent = new TestEvent("id1");
			var serializedEvent = "{\"eventId\":\"id1\"}";

			when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
			when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

			var publication = TargetEventPublication.of(testEvent, TARGET_IDENTIFIER);

			repository.create(publication);
			repository.markCompleted(publication, Instant.now());

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

			assertThat(actual).isEmpty();
		}

		@Test // GH-3
		void shouldReturnTheOldestEvent() throws Exception {

			var testEvent = new TestEvent("id");
			var serializedEvent = "{\"eventId\":\"id\"}";

			when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
			when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

			var publication = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));
			Thread.sleep(10);
			repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

			var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

			assertThat(actual).hasValueSatisfying(it -> {
				assertThat(it.getPublicationDate()) //
						.isCloseTo(publication.getPublicationDate(), within(1, ChronoUnit.MILLIS));
			});
		}

		@Test // GH-3
		void shouldSilentlyIgnoreNotSerializableEvents() {

			var testEvent = new TestEvent("id");
			var serializedEvent = "{\"eventId\":\"id\"}";

			when(serializer.serialize(testEvent)).thenReturn(serializedEvent);
			when(serializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

			// Store publication
			repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

			operations.update("UPDATE " + table() + " SET EVENT_TYPE='abc'");

			assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
					.isEmpty();
		}

		@Test // GH-20
		void shouldDeleteCompletedEvents() {

			var testEvent1 = new TestEvent("abc");
			var serializedEvent1 = "{\"eventId\":\"abc\"}";
			var testEvent2 = new TestEvent("def");
			var serializedEvent2 = "{\"eventId\":\"def\"}";

			when(serializer.serialize(testEvent1)).thenReturn(serializedEvent1);
			when(serializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
			when(serializer.serialize(testEvent2)).thenReturn(serializedEvent2);
			when(serializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

			var publication = repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));
			repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));

			repository.markCompleted(publication, Instant.now());

			repository.deleteCompletedPublications();

			assertThat(operations.query("SELECT * FROM " + table(), (rs, __) -> rs.getString("SERIALIZED_EVENT")))
					.hasSize(1).element(0).isEqualTo(serializedEvent2);

			if (properties.isArchiveCompletion()) {
				assertThat(operations.query("SELECT * FROM " + archiveTable(), (rs, __) -> rs.getString("SERIALIZED_EVENT")))
						.hasSize(0);
			}

		}

		@Test // GH-251
		void shouldDeleteCompletedEventsBefore() {

			assumeFalse(properties.isDeleteCompletion());

			var testEvent1 = new TestEvent("abc");
			var serializedEvent1 = "{\"eventId\":\"abc\"}";
			var testEvent2 = new TestEvent("def");
			var serializedEvent2 = "{\"eventId\":\"def\"}";

			when(serializer.serialize(testEvent1)).thenReturn(serializedEvent1);
			when(serializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
			when(serializer.serialize(testEvent2)).thenReturn(serializedEvent2);
			when(serializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

			repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));
			repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));

			var now = Instant.now();

			repository.markCompleted(testEvent1, TARGET_IDENTIFIER, now.minusSeconds(30));
			repository.markCompleted(testEvent2, TARGET_IDENTIFIER, now);

			repository.deleteCompletedPublicationsBefore(now.minusSeconds(15));

			var table = properties.isArchiveCompletion() ? archiveTable() : table();

			assertThat(operations.query("SELECT * FROM " + table, (rs, __) -> rs.getString("SERIALIZED_EVENT")))
					.hasSize(1).element(0).isEqualTo(serializedEvent2);
		}

		@Test // GH-294
		void deletesPublicationsByIdentifier() {

			var first = createPublication(new TestEvent("first"));
			var second = createPublication(new TestEvent("second"));
			var third = createPublication(new TestEvent("third"));

			repository.deletePublications(List.of(first.getIdentifier(), second.getIdentifier()));

			assertThat(repository.findIncompletePublications())
					.hasSize(1)
					.element(0)
					.matches(it -> it.getIdentifier().equals(third.getIdentifier()))
					.matches(it -> it.getEvent().equals(third.getEvent()));
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

			if (properties.isDeleteCompletion()) {

				assertThat(repository.findCompletedPublications()).isEmpty();
				assertThat(repository.findIncompletePublications()).isEmpty();

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

			if (properties.isDeleteCompletion()) {

				assertThat(repository.findCompletedPublications()).isEmpty();

			} else {

				assertThat(repository.findCompletedPublications())
						.extracting(TargetEventPublication::getIdentifier)
						.containsExactly(publication.getIdentifier());
			}

			if (properties.isArchiveCompletion()) {
				assertThat(operations.queryForObject("SELECT COUNT(*) FROM " + archiveTable(), int.class)).isOne();
			}
		}

		@Test // GH-753
		void returnsSameEventInstanceFromPublication() {

			// An event not implementing equals(…) / hashCode()
			var event = new Sample();

			// Serialize to whatever
			doReturn("sample").when(serializer).serialize(event);

			// Return fresh instances for every deserialization attempt
			doAnswer(__ -> new Sample()).when(serializer).deserialize("sample", Sample.class);

			repository.create(TargetEventPublication.of(event, TARGET_IDENTIFIER));

			var publication = repository.findIncompletePublications().get(0);

			assertThat(publication.getEvent()).isSameAs(publication.getEvent());
		}

		String table() {
			return "EVENT_PUBLICATION";
		}

		String archiveTable() { return table() + "_ARCHIVE"; }

		private TargetEventPublication createPublication(Object event) {

			var token = event.toString();

			doReturn(token).when(serializer).serialize(event);
			doReturn(event).when(serializer).deserialize(token, event.getClass());

			return repository.create(TargetEventPublication.of(event, TARGET_IDENTIFIER));
		}
	}

	@JdbcTest(properties = "spring.modulith.events.jdbc.schema-initialization.enabled=true")
	static abstract class WithNoDefinedSchemaName extends TestBase {}

	@JdbcTest(properties = { "spring.modulith.events.jdbc.schema-initialization.enabled=true",
			"spring.modulith.events.jdbc.schema=test" })
	static abstract class WithDefinedSchemaName extends TestBase {

		@Override
		String table() {
			return "test." + super.table();
		}
	}

	@JdbcTest(properties = { "spring.modulith.events.jdbc.schema-initialization.enabled=true",
			"spring.modulith.events.jdbc.schema=" })
	static abstract class WithEmptySchemaName extends TestBase {}

	@JdbcTest(properties = { "spring.modulith.events.jdbc.schema-initialization.enabled=true",
			CompletionMode.PROPERTY + "=DELETE" })
	static abstract class WithDeleteCompletion extends TestBase {}

	@JdbcTest(properties = { "spring.modulith.events.jdbc.schema-initialization.enabled=true",
			CompletionMode.PROPERTY + "=ARCHIVE" })
	static abstract class WithArchiveCompletion extends TestBase {

		@Override
		String archiveTable() {
			return "EVENT_PUBLICATION_ARCHIVE";
		}
	}

	// HSQL

	@WithHsql
	class HsqlWithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithHsql
	class HsqlWithDefinedSchemaName extends WithDefinedSchemaName {}

	@WithHsql
	class HsqlWithEmptySchemaName extends WithEmptySchemaName {}

	@WithHsql
	class HsqlWithDeleteCoqmpletion extends WithDeleteCompletion {}

	@WithHsql
	class HsqlWithArchiveCompletion extends WithArchiveCompletion {}

	// H2

	@WithH2
	class H2WithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithH2
	class H2WithDefinedSchemaName extends WithDefinedSchemaName {}

	@WithH2
	class H2WithEmptySchemaName extends WithEmptySchemaName {}

	@WithH2
	class H2WithDeleteCompletion extends WithDeleteCompletion {}

	@WithH2
	class H2WithArchiveCompletion extends WithArchiveCompletion {}

	// Postgres

	@WithPostgres
	class PostgresWithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithPostgres
	class PostgresWithDefinedSchemaName extends WithDefinedSchemaName {}

	@WithPostgres
	class PostgresWithEmptySchemaName extends WithEmptySchemaName {}

	@WithPostgres
	class PostgresWithDeleteCompletion extends WithDeleteCompletion {}

	@WithPostgres
	class PostgresWithArchiveCompletion extends WithArchiveCompletion {}

	// MySQL

	@WithMySql
	class MysqlWithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithMySql
	class MysqlWithDeleteCompletion extends WithDeleteCompletion {}

	@WithMySql
	class MysqlWithArchiveCompletion extends WithArchiveCompletion {}

	// MariaDB

	@WithMariaDB
	class MariaDBWithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithMariaDB
	class MariaDBWithDeleteCompletion extends WithDeleteCompletion {}

	@WithMariaDB
	class MariaDBWithArchiveCompletion extends WithArchiveCompletion {}

	// MSSQL

	@WithMssql
	class MssqlWithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithMssql
	class MssqlWithDeleteCompletion extends WithDeleteCompletion {}

	@WithMssql
	class MssqlWithArchiveCompletion extends WithArchiveCompletion {}

	// Oracle

	@WithOracle
	class OracleWithNoDefinedSchemaName extends WithNoDefinedSchemaName {}

	@WithOracle
	class OracleWithDeleteCompletion extends WithDeleteCompletion {}

	@WithOracle
	class OracleWithArchiveCompletion extends WithArchiveCompletion {}

	private record TestEvent(String eventId) {}

	private static final class Sample {}

	@Nested
	@ActiveProfiles("h2")
	@Testcontainers(disabledWithoutDocker = false)
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithH2 {}

	@Nested
	@ActiveProfiles("hsql")
	@Testcontainers(disabledWithoutDocker = false)
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithHsql {}

	@Nested
	@ActiveProfiles("mysql")
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithMySql {}

	@Nested
	@ActiveProfiles("mariadb")
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithMariaDB {}

	@Nested
	@ActiveProfiles("postgres")
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithPostgres {}

	@Nested
	@ActiveProfiles("mssql")
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithMssql {}

	@Nested
	@ActiveProfiles("oracle")
	@Retention(RetentionPolicy.RUNTIME)
	@interface WithOracle {}
}
