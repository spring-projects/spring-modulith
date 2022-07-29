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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
class DatabaseSchemaInitializerIntegrationTests {

	private static final String COUNT_PUBLICATIONS = "SELECT COUNT(*) FROM EVENT_PUBLICATION";

	@ImportAutoConfiguration(JdbcEventPublicationAutoConfiguration.class)
	@ContextConfiguration(classes = TestApplication.class)
	static class TestBase {
		@MockBean EventSerializer serializer;
	}

	@JdbcTest(properties = "spring.modulith.events.schema-initialization.enabled=true")
	abstract static class WithInitEnabled extends TestBase {

		@Autowired JdbcOperations operations;
		@Autowired Optional<DatabaseSchemaInitializer> initializer;

		@Test // GH-3
		void doesRegisterAnInitializerBean() {
			assertThat(initializer).isPresent();
		}

		@Test // GH-3
		void shouldCreateDatabaseSchemaOnStartUp() {
			assertThat(operations.queryForObject(COUNT_PUBLICATIONS, Long.class)).isEqualTo(0);
		}
	}

	@Nested
	@JdbcTest(properties = "spring.modulith.events.schema-initialization.enabled=false")
	class WithInitDisabled extends TestBase {

		@SpyBean JdbcOperations operations;
		@Autowired Optional<DatabaseSchemaInitializer> initializer;

		@Test // GH-3
		void doesNotRegisterAnInitializerBean() {
			assertThat(initializer).isEmpty();
		}

		@Test // GH-3
		void shouldNotCreateDatabaseSchemaOnStartUp() {
			verify(operations, never()).execute(anyString());
		}
	}

	@JdbcTest
	abstract static class InitializationDisabledByDefault extends TestBase {

		@SpyBean JdbcOperations operations;
		@Autowired Optional<DatabaseSchemaInitializer> initializer;

		@Test // GH-3
		void doesNotRegisterAnInitializerBean() {
			assertThat(initializer).isEmpty();
		}

		@Test // GH-3
		void shouldNotCreateDatabaseSchemaOnStartUp() {
			verify(operations, never()).execute(anyString());
		}
	}

	@JdbcTest
	abstract static class InitializationEnabledByDefault extends TestBase {

		@SpyBean JdbcOperations operations;
		@Autowired Optional<DatabaseSchemaInitializer> initializer;

		@Test // GH-3
		void doesRegisterAnInitializerBean() {
			assertThat(initializer).isPresent();
		}

		@Test // GH-3
		void shouldCreateDatabaseSchemaOnStartUp() {
			assertThat(operations.queryForObject(COUNT_PUBLICATIONS, Long.class)).isEqualTo(0);
		}
	}

	@Nested
	@ActiveProfiles("hsqldb")
	class HSQLDB extends WithInitEnabled {}

	@Nested
	@ActiveProfiles("h2")
	class H2 extends WithInitEnabled {}

	@Nested
	@ActiveProfiles("postgres")
	class Postgres extends WithInitEnabled {}

	@Nested
	@ActiveProfiles("mysql")
	class MySQL extends WithInitEnabled {}

	@Nested
	@ActiveProfiles("h2")
	class H2EnabledByDefault extends InitializationEnabledByDefault {}

	@Nested
	@ActiveProfiles("postgres")
	class PostgresDisabledByDefault extends InitializationDisabledByDefault {}
}
