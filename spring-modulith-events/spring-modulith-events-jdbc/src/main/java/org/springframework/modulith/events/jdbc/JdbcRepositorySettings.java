/*
 * Copyright 2024-2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.util.Assert;

/**
 * Internal abstraction of customization options for {@link JdbcEventPublicationRepository}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 * @soundtrack Jeff Coffin - Bom Bom (Only the Horizon)
 */
public class JdbcRepositorySettings {

	private final DatabaseType databaseType;
	private final @Nullable String schema;
	private final CompletionMode completionMode;

	/**
	 * Creates a new {@link JdbcRepositorySettings} for the given {@link DatabaseType}, {@link CompletionMode} and schema
	 *
	 * @param databaseType must not be {@literal null}.
	 * @param schema can be {@literal null}
	 * @param completionMode must not be {@literal null}.
	 */
	JdbcRepositorySettings(DatabaseType databaseType, CompletionMode completionMode, @Nullable String schema) {

		Assert.notNull(databaseType, "Database type must not be null!");
		Assert.notNull(completionMode, "Completion mode must not be null!");

		this.databaseType = databaseType;
		this.schema = schema;
		this.completionMode = completionMode;

		if (schema != null && !databaseType.isSchemaSupported()) {
			throw new IllegalStateException(DatabaseType.SCHEMA_NOT_SUPPORTED);
		}
	}

	/**
	 * Returns the {@link DatabaseType}.
	 *
	 * @return will never be {@literal null}.
	 */
	public DatabaseType getDatabaseType() {
		return databaseType;
	}

	/**
	 * Return the schema to be used.
	 *
	 * @return can be {@literal null}.
	 */
	public @Nullable String getSchema() {
		return schema;
	}

	/**
	 * Returns whether we use the deleting completion mode.
	 */
	public boolean isDeleteCompletion() {
		return completionMode == CompletionMode.DELETE;
	}

	/**
	 * Returns whether we use the archiving completion mode.
	 */
	public boolean isArchiveCompletion() {
		return completionMode == CompletionMode.ARCHIVE;
	}

	/**
	 * Returns whether we use the updating completion mode.
	 */
	public boolean isUpdateCompletion() {
		return completionMode == CompletionMode.UPDATE;
	}
}
