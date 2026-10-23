/*
 * Copyright 2026 the original author or authors.
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

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.jdbc.JdbcConfigurationProperties.SchemaInitialization;

/**
 * Unit tests for {@link JdbcEventPublicationAutoConfiguration}.
 *
 * @author Kirill Adzerikho
 */
class JdbcEventPublicationAutoConfigurationUnitTests {

	@Test // GH-924
	void usesConfiguredDatabaseTypeWithoutTouchingTheDataSource() {

		var properties = new JdbcConfigurationProperties(new SchemaInitialization(false), null, null,
				DatabaseType.POSTGRES);
		var configuration = new JdbcEventPublicationAutoConfiguration();

		var dataSource = mock(DataSource.class, invocation -> {
			throw new AssertionError("DataSource must not be touched!");
		});

		assertThat(configuration.databaseType(dataSource, properties)).isEqualTo(DatabaseType.POSTGRES);
	}

	@Test // GH-924
	void detectsDatabaseTypeFromDataSourceWithoutConfiguredType() throws Exception {

		var properties = new JdbcConfigurationProperties(new SchemaInitialization(false), null, null, null);
		var configuration = new JdbcEventPublicationAutoConfiguration();

		var dataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
		when(dataSource.getConnection().getMetaData().getDatabaseProductName()).thenReturn("PostgreSQL");

		assertThat(configuration.databaseType(dataSource, properties)).isEqualTo(DatabaseType.POSTGRES);
	}
}
