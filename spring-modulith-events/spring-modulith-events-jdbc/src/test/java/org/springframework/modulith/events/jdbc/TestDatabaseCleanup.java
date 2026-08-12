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

import java.sql.DriverManager;
import java.sql.SQLException;

import org.testcontainers.jdbc.ContainerDatabaseDriver;

/**
 * Tears down the databases shared between {@link JdbcEventPublicationRepositoryIntegrationTests} and
 * {@link JdbcEventPublicationRepositoryV2IntegrationTests} so that whichever of the two suites runs second starts
 * against a fresh schema instead of reusing one created by the legacy/V2 structure of the other.
 *
 * @author Oliver Drotbohm
 */
class TestDatabaseCleanup {

	private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

	static void wipeAll() {

		// Tears down all containers started via the jdbc:tc:… URL scheme (Postgres, MySQL, MariaDB, MSSQL, Oracle).
		ContainerDatabaseDriver.killContainers();

		// H2 is kept alive by DB_CLOSE_DELAY=-1 independently of the tc: driver, and needs an explicit shutdown.
		try (var connection = DriverManager.getConnection(H2_URL)) {
			connection.createStatement().execute("SHUTDOWN");
		} catch (SQLException o_O) {
			// No H2 database was active; nothing to shut down.
		}
	}
}
