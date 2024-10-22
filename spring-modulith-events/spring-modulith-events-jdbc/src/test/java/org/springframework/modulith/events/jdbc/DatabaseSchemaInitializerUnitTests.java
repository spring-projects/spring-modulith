/*
 * Copyright 2024 the original author or authors.
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

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.jdbc.JdbcConfigurationProperties.SchemaInitialization;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * Unit tests for {@link DatabaseSchemaInitializer}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSchemaInitializerUnitTests {

	@Mock DataSource dataSource;
	@Mock ResourceLoader resourceLoader;
	@Mock JdbcTemplate jdbcTemplate;

	// GH-685
	@ParameterizedTest
	@ValueSource(strings = { "", "test" })
	void rejectsExplicitSchemaNameForMySql(String schema) {
		assertThatIllegalStateException().isThrownBy(() -> createInitializer(withSchema(schema), DatabaseType.MYSQL));
	}

	// GH-836
	@ParameterizedTest
	@ValueSource(strings = { "", "test" })
	void rejectsExplicitSchemaNameForMariaDB(String schema) {
		assertThatIllegalStateException().isThrownBy(() -> createInitializer(withSchema(schema), DatabaseType.MARIADB));
	}

	// GH-804
	@ParameterizedTest
	@ValueSource(strings = { "", "test" })
	void rejectsExplicitSchemaNameForMSSql(String schema) {
		assertThatIllegalStateException().isThrownBy(() -> createInitializer(withSchema(schema), DatabaseType.MSSQL));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "test" })
	void rejectsExplicitSchemaNameForOracle(String schema) {
		assertThatIllegalStateException().isThrownBy(() -> createInitializer(withSchema(schema), DatabaseType.ORACLE));
	}

	private DatabaseSchemaInitializer createInitializer(JdbcConfigurationProperties properties, DatabaseType type) {

		var settings = new JdbcRepositorySettings(type, CompletionMode.UPDATE, properties.getSchema());

		return new DatabaseSchemaInitializer(dataSource, resourceLoader, jdbcTemplate, settings);
	}

	private static JdbcConfigurationProperties withSchema(String schema) {
		return new JdbcConfigurationProperties(new SchemaInitialization(true), schema);
	}
}
