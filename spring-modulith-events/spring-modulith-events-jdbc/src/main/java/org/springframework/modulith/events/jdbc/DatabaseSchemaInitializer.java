/*
 * Copyright 2022-2025 the original author or authors.
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

import java.sql.Connection;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Initializes the DB schema used to store events
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 * @author Raed Ben Hamouda
 */
class DatabaseSchemaInitializer implements InitializingBean {

	private final DataSource dataSource;
	private final ResourceLoader resourceLoader;
	private final JdbcOperations jdbcOperations;
	private final JdbcRepositorySettings settings;

	DatabaseSchemaInitializer(DataSource dataSource, ResourceLoader resourceLoader, JdbcOperations jdbcOperations,
			JdbcRepositorySettings settings) {

		this.dataSource = dataSource;
		this.resourceLoader = resourceLoader;
		this.jdbcOperations = jdbcOperations;
		this.settings = settings;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {

		try (Connection connection = dataSource.getConnection()) {

			var initialSchema = connection.getSchema();
			var schemaName = settings.getSchema();
			var databaseType = settings.getDatabaseType();
			var useSchema = schemaName != null && !schemaName.isEmpty();

			if (useSchema) { // A schema name has been specified.

				if (eventPublicationTableExists(Objects.requireNonNull(schemaName))) {
					return;
				}

				jdbcOperations.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
				jdbcOperations.execute(databaseType.getSetSchemaSql(schemaName));
			}

			var locator = new DatabaseSchemaLocator(resourceLoader);

			var populator = new ResourceDatabasePopulator();
			locator.getSchemaResource(settings).forEach(populator::addScript);
			populator.execute(dataSource);

			// Return to the initial schema.
			if (initialSchema != null && useSchema) {
				jdbcOperations.execute(databaseType.getSetSchemaSql(initialSchema));
			}
		}
	}

	private boolean eventPublicationTableExists(String schema) {

		var query = """
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = ? AND table_name = 'EVENT_PUBLICATION'
				""";

		var count = jdbcOperations.queryForObject(query, Integer.class, schema);

		return count != null && count > 0;
	}
}
