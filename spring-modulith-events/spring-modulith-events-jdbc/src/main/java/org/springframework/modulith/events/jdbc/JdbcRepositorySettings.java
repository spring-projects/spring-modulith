/*
 * Copyright 2024-2026 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

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
	private final boolean useLegacyStructure;

	/**
	 * Creates a new {@link JdbcRepositorySettings} for the given {@link DatabaseType}, {@link CompletionMode} and schema
	 *
	 * @param databaseType must not be {@literal null}.
	 * @param completionMode must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	JdbcRepositorySettings(DatabaseType databaseType, CompletionMode completionMode,
			JdbcConfigurationProperties properties) {

		Assert.notNull(databaseType, "Database type must not be null!");
		Assert.notNull(completionMode, "Completion mode must not be null!");
		Assert.notNull(properties, "JdbcConfigurationProperties must not be null!");

		this.databaseType = databaseType;
		this.schema = properties.getSchema();
		this.completionMode = completionMode;
		this.useLegacyStructure = properties.isUseLegacyStructure();

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

	/**
	 * Load the schema {@link Resource}s to be used.
	 *
	 * @param loader must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 2.0
	 */
	List<Resource> loadSchema(Function<String, Resource> loader) {

		var schemaResourceFilename = databaseType.getSchemaResourceFilename(useLegacyStructure);
		var schemaResource = loader.apply(schemaResourceFilename);

		if (!isArchiveCompletion()) {
			return Collections.singletonList(schemaResource);
		}

		var archiveSchemaResourceFilename = databaseType.getArchiveSchemaResourceFilename(useLegacyStructure);

		return List.of(schemaResource, loader.apply(archiveSchemaResourceFilename));
	}

	SchemaVersion getSchemaVersion() {
		return useLegacyStructure ? SchemaVersion.V1 : SchemaVersion.V2;
	}

	String getTable() {
		return ObjectUtils.isEmpty(schema) ? "EVENT_PUBLICATION" : schema + ".EVENT_PUBLICATION";
	}

	String getArchiveTable() {
		return isArchiveCompletion() ? getTable() + "_ARCHIVE" : getTable();
	}

	enum SchemaVersion {
		V1, V2;
	}
}
