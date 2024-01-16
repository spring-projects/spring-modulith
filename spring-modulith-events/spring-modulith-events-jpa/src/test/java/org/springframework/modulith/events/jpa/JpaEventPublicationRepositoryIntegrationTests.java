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
package org.springframework.modulith.events.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Oliver Drotbohm
 * @author Dmitry Belyaev
 * @author Björn Kieling
 */
@ExtendWith(SpringExtension.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@RequiredArgsConstructor
class JpaEventPublicationRepositoryIntegrationTests {

	private static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");
	private static final EventSerializer eventSerializer = mock(EventSerializer.class);

	@Configuration
	@Import(JpaEventPublicationConfiguration.class)
	static class TestConfig {

		@Bean
		EventSerializer eventSerializer() {
			return eventSerializer;
		}

		// Database

		@Bean
		EmbeddedDatabase hsqlDatabase() {
			return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.HSQL).build();
		}

		// JPA

		@Bean
		LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {

			AbstractJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
			vendor.setGenerateDdl(true);

			LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
			factory.setJpaVendorAdapter(vendor);
			factory.setDataSource(dataSource);
			factory.setPackagesToScan(getClass().getPackage().getName());

			return factory;
		}

		@Bean
		EntityManager entityManager(EntityManagerFactory factory) {
			return SharedEntityManagerCreator.createSharedEntityManager(factory);
		}

		@Bean
		JpaTransactionManager transactionManager(EntityManagerFactory factory) {
			return new JpaTransactionManager(factory);
		}
	}

	private final JpaEventPublicationRepository repository;
	private final EntityManager em;

	@AfterEach
	public void flush() {
		em.flush();
	}

	@Test
	void persistsJpaEventPublication() {

		var testEvent = new TestEvent("abc");
		var serializedEvent = "{\"eventId\":\"abc\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		var publication = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

		var eventPublications = repository.findIncompletePublications();

		assertThat(eventPublications).hasSize(1);
		assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
		assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());
		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER))
				.isPresent();

		repository.markCompleted(publication, Instant.now());

		assertThat(repository.findIncompletePublications()).isEmpty();
	}

	@Test // GH-25
	void shouldTolerateEmptyResult() {

		var testEvent = new TestEvent("id");
		var serializedEvent = "{\"eventId\":\"id\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);

		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER)).isEmpty();
	}

	@Test // GH-25
	void shouldNotReturnCompletedEvents() {

		TestEvent testEvent = new TestEvent("abc");
		String serializedEvent = "{\"eventId\":\"abc\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		var publication = TargetEventPublication.of(testEvent, TARGET_IDENTIFIER);

		repository.create(publication);
		repository.markCompleted(publication, Instant.now());

		var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

		assertThat(actual).isEmpty();
	}

	@Test // GH-20
	void shouldDeleteCompletedEvents() {

		var testEvent1 = new TestEvent("abc");
		var serializedEvent1 = "{\"eventId\":\"abc\"}";
		var testEvent2 = new TestEvent("def");
		var serializedEvent2 = "{\"eventId\":\"def\"}";

		when(eventSerializer.serialize(testEvent1)).thenReturn(serializedEvent1);
		when(eventSerializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
		when(eventSerializer.serialize(testEvent2)).thenReturn(serializedEvent2);
		when(eventSerializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

		repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));
		repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));
		repository.markCompleted(testEvent1, TARGET_IDENTIFIER, Instant.now());
		repository.deleteCompletedPublications();

		assertThat(em.createQuery("select p from JpaEventPublication p", JpaEventPublication.class).getResultList())
				.hasSize(1) //
				.element(0).extracting(it -> it.serializedEvent).isEqualTo(serializedEvent2);
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

	@Test // GH-251
	void shouldDeleteCompletedEventsBefore() {

		var testEvent1 = new TestEvent("abc");
		var serializedEvent1 = "{\"eventId\":\"abc\"}";
		var testEvent2 = new TestEvent("def");
		var serializedEvent2 = "{\"eventId\":\"def\"}";

		when(eventSerializer.serialize(testEvent1)).thenReturn(serializedEvent1);
		when(eventSerializer.deserialize(serializedEvent1, TestEvent.class)).thenReturn(testEvent1);
		when(eventSerializer.serialize(testEvent2)).thenReturn(serializedEvent2);
		when(eventSerializer.deserialize(serializedEvent2, TestEvent.class)).thenReturn(testEvent2);

		repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));
		repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));

		var now = Instant.now();

		repository.markCompleted(testEvent1, TARGET_IDENTIFIER, now.minusSeconds(30));
		repository.markCompleted(testEvent2, TARGET_IDENTIFIER, now);
		repository.deleteCompletedPublicationsBefore(now.minusSeconds(15));

		assertThat(em.createQuery("select p from JpaEventPublication p", JpaEventPublication.class).getResultList())
				.hasSize(1) //
				.element(0).extracting(it -> it.serializedEvent).isEqualTo(serializedEvent2);
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

		assertThat(repository.findCompletedPublications())
				.hasSize(1)
				.element(0)
				.extracting(TargetEventPublication::getEvent)
				.isEqualTo(event);
	}

	private TargetEventPublication createPublication(Object event) {

		var token = event.toString();

		doReturn(token).when(eventSerializer).serialize(event);
		doReturn(event).when(eventSerializer).deserialize(token, event.getClass());

		return repository.create(TargetEventPublication.of(event, TARGET_IDENTIFIER));
	}

	private void savePublicationAt(LocalDateTime date) {
		em.persist(new JpaEventPublication(UUID.randomUUID(), date.toInstant(ZoneOffset.UTC), "", "", Object.class));
	}

	@Value
	private static final class TestEvent {
		String eventId;
	}
}
