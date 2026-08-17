/*
 * Copyright 2022-2026 the original author or authors.
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

import java.sql.SQLException;

import javax.sql.DataSource;

import org.testcontainers.jdbc.ContainerDatabaseDriver;

/**
 * Tears down the databases shared between {@link JdbcEventPublicationRepositoryIntegrationTests} and
 * {@link JdbcEventPublicationRepositoryV2IntegrationTests} so that whichever of the two suites runs second starts
 * against a fresh schema instead of reusing one created by the legacy/V2 structure of the other.
 *
 * @author Oliver Drotbohm
 */
class TestDatabaseCleanup {

	/**
	 * Tears down all containers started via the {@code jdbc:tc:…} URL scheme (Postgres, MySQL, MariaDB, MSSQL,
	 * Oracle). Cheap to call only once per test class, as containers are otherwise reused across {@code @Nested}
	 * classes sharing the same JDBC URL.
	 */
	static void killContainers() {
		ContainerDatabaseDriver.killContainers();
	}

	/**
	 * Shuts down the H2 in-memory database backing the given {@link DataSource}, which is kept alive by
	 * {@code DB_CLOSE_DELAY=-1} independently of the {@code jdbc:tc:} driver. Goes through the {@link DataSource}
	 * actually used by the tests so that it picks up whatever credentials Spring Boot configured for it, instead of
	 * guessing them.
	 *
	 * @param dataSource must not be {@literal null}.
	 */
	static void shutdownH2(DataSource dataSource) {

		try (var connection = dataSource.getConnection()) {
			connection.createStatement().execute("SHUTDOWN");
		} catch (SQLException o_O) {
			// No H2 database was active; nothing to shut down.
		}
	}
}
