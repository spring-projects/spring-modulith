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
package org.springframework.modulith.events.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
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

	@Test
	void persistsJpaEventPublication() {

		TestEvent testEvent = new TestEvent("abc");
		String serializedEvent = "{\"eventId\":\"abc\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		CompletableEventPublication publication = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

		// Store publication
		repository.create(publication);

		List<EventPublication> eventPublications = repository.findIncompletePublications();
		assertThat(eventPublications).hasSize(1);
		assertThat(eventPublications.get(0).getEvent()).isEqualTo(publication.getEvent());
		assertThat(eventPublications.get(0).getTargetIdentifier()).isEqualTo(publication.getTargetIdentifier());
		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER)).isPresent();

		// Complete publication
		repository.update(publication.markCompleted());

		assertThat(repository.findIncompletePublications()).isEmpty();
	}

	@Test
	void shouldTolerateEmptyResult() {

		var testEvent = new TestEvent("id");
		var serializedEvent = "{\"eventId\":\"id\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);

		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER)).isEmpty();
	}

	@Test
	void shouldNotReturnCompletedEvents() {

		TestEvent testEvent = new TestEvent("abc");
		String serializedEvent = "{\"eventId\":\"abc\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(serializedEvent);
		when(eventSerializer.deserialize(serializedEvent, TestEvent.class)).thenReturn(testEvent);

		CompletableEventPublication publication = CompletableEventPublication.of(testEvent, TARGET_IDENTIFIER);

		// Store publication
		repository.create(publication);
		repository.update(publication.markCompleted());

		var actual = repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, TARGET_IDENTIFIER);

		assertThat(actual).isEmpty();
	}

	@Value
	private static final class TestEvent {
		String eventId;
	}
}
