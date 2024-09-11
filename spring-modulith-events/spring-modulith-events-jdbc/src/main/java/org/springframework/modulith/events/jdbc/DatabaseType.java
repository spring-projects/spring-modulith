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

import java.util.Arrays;
import java.util.UUID;

import org.springframework.util.Assert;

/**
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 * @author Raed Ben Hamouda
 */
enum DatabaseType {

	HSQLDB("hsqldb", "HSQL Database Engine"),

	H2("h2", "H2"),

	MYSQL("mysql", "MySQL") {

		@Override
		Object uuidToDatabase(UUID id) {
			return id.toString();
		}

		@Override
		UUID databaseToUUID(Object id) {
			return UUID.fromString(id.toString());
		}

		@Override
		boolean isSchemaSupported() {
			return false;
		}
	},

	POSTGRES("postgresql", "PostgreSQL"),

	MSSQL("sqlserver", "Microsoft SQL Server") {

		@Override
		Object uuidToDatabase(UUID id) {
			return id.toString();
		}

		@Override
		UUID databaseToUUID(Object id) {
			return UUID.fromString(id.toString());
		}

		@Override
		boolean isSchemaSupported() {
			return false;
		}
	};

	static final String SCHEMA_NOT_SUPPORTED = "Setting the schema name not supported on MySQL!";

	static DatabaseType from(String productName) {

		return Arrays.stream(DatabaseType.values())
				.filter(it -> it.fullName.equalsIgnoreCase(productName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unsupported database type: " + productName));
	}

	private final String value, fullName;

	DatabaseType(String value, String fullName) {
		this.value = value;
		this.fullName = fullName;
	}

	Object uuidToDatabase(UUID id) {
		return id;
	}

	UUID databaseToUUID(Object id) {

		Assert.isInstanceOf(UUID.class, id, "Database value not of type UUID!");

		return (UUID) id;
	}

	String getSchemaResourceFilename() {
		return "/schema-" + value + ".sql";
	}

	boolean isSchemaSupported() {
		return true;
	}

	String getSetSchemaSql(String schema) {

		if (!isSchemaSupported()) {
			throw new IllegalArgumentException(SCHEMA_NOT_SUPPORTED);
		}

		return switch (this) {

			case H2, HSQLDB -> "SET SCHEMA " + schema;
			case POSTGRES -> "SET search_path TO " + schema;
			default -> throw new IllegalArgumentException(SCHEMA_NOT_SUPPORTED);
		};
	}
}
