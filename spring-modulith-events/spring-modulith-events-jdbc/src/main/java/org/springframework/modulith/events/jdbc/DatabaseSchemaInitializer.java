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

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.ObjectUtils;

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
	private final DatabaseType databaseType;
	private final JdbcTemplate jdbcTemplate;
	private final JdbcConfigurationProperties properties;

	DatabaseSchemaInitializer(DataSource dataSource, ResourceLoader resourceLoader, DatabaseType databaseType,
  			JdbcTemplate jdbcTemplate, JdbcConfigurationProperties properties) {

		this.dataSource = dataSource;
		this.resourceLoader = resourceLoader;
		this.databaseType = databaseType;
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		String initialSchema;
		try (Connection connection = dataSource.getConnection()) {

			initialSchema = connection.getSchema();

			String schemaName = properties.getSchema();
			if (!ObjectUtils.isEmpty(schemaName)) { // A schema name has been specified.

				if (eventPublicationTableExists(jdbcTemplate, schemaName)) {
					return;
				}

				jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
				jdbcTemplate.execute(databaseType.sqlStatementSetSchema(schemaName));
			}

			var locator = new DatabaseSchemaLocator(resourceLoader);
			new ResourceDatabasePopulator(locator.getSchemaResource(databaseType)).execute(dataSource);

			// Return to the initial schema.
			if (initialSchema != null) {
				jdbcTemplate.execute(databaseType.sqlStatementSetSchema(initialSchema));
			}
		}
	}

	private boolean eventPublicationTableExists(JdbcTemplate jdbcTemplate, String schema) {
		String query = """
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = ? AND table_name = 'EVENT_PUBLICATION'
				""";
		Integer count = jdbcTemplate.queryForObject(query, Integer.class, schema);
		return count != null && count > 0;
	}
}
