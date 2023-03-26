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
package org.springframework.modulith.events.jdbc;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
enum DatabaseType {

	HSQLDB("hsqldb"),

	H2("h2"),

	MYSQL("mysql") {

		@Override
		Object uuidToDatabase(UUID id) {
			return id.toString();
		}

		@Override
		UUID databaseToUUID(Object id) {
			return UUID.fromString(id.toString());
		}
	},

	POSTGRES("postgresql");

	private static final Map<DatabaseDriver, DatabaseType> DATABASE_DRIVER_TO_DATABASE_TYPE_MAP = //
			Map.of( //
					DatabaseDriver.H2, H2, //
					DatabaseDriver.HSQLDB, HSQLDB, //
					DatabaseDriver.POSTGRESQL, POSTGRES, //
					DatabaseDriver.MYSQL, MYSQL);

	static DatabaseType from(DatabaseDriver databaseDriver) {

		var databaseType = DATABASE_DRIVER_TO_DATABASE_TYPE_MAP.get(databaseDriver);

		if (databaseType == null) {
			throw new IllegalArgumentException("Unsupported database type: " + databaseDriver);
		}

		return databaseType;
	}

	private final String value;

	DatabaseType(String value) {
		this.value = value;
	}

	Object uuidToDatabase(UUID id) {
		return id;
	}

	UUID databaseToUUID(Object id) {

		Assert.isInstanceOf(UUID.class, id, "Database value not of type UUID!");

		return (UUID) id;
	}

	String getSchemaResourceFilename() {
		return ResourceLoader.CLASSPATH_URL_PREFIX + "/schema-" + value + ".sql";
	}
}
