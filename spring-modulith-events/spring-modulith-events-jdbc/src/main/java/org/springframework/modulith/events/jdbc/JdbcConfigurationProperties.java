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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for JDBC.
 *
 * @author Raed Ben Hamouda
 * @author Oliver Drotbohm
 */
@ConfigurationProperties(prefix = "spring.modulith.events.jdbc")
class JdbcConfigurationProperties {

	private final SchemaInitialization schemaInitialization;
	private final @Nullable String schema;
	private final boolean useLegacyStructure;

	/**
	 * Creates a new {@link JdbcConfigurationProperties} instance.
	 *
	 * @param schemaInitialization whether to initialize the JDBC event publication schema. Defaults to {@literal false}.
	 * @param schema the schema name of event publication table, can be {@literal null}.
	 */
	@ConstructorBinding
	JdbcConfigurationProperties(SchemaInitialization schemaInitialization, @Nullable String schema,
			@Nullable Boolean useLegacyStructure) {

		this.schemaInitialization = schemaInitialization;
		this.schema = schema;
		this.useLegacyStructure = useLegacyStructure == null ? false : useLegacyStructure.booleanValue();
	}

	/**
	 * Whether to initialize the JDBC event publication schema.
	 */
	public SchemaInitialization getSchemaInitialization() {
		return schemaInitialization;
	}

	/**
	 * The name of the schema where the event publication table resides.
	 *
	 * @return can be {@literal null}.
	 */
	public @Nullable String getSchema() {
		return schema;
	}

	/**
	 * Whether to use the legacy event publication database schema.
	 *
	 * @since 2.0
	 */
	public boolean isUseLegacyStructure() {
		return useLegacyStructure;
	}

	void verify(DatabaseType databaseType) {

		if (schema != null && !databaseType.isSchemaSupported()) {
			throw new IllegalStateException(DatabaseType.SCHEMA_NOT_SUPPORTED);
		}
	}

	static class SchemaInitialization {

		private final boolean enabled;

		/**
		 * @param enabled
		 */
		@ConstructorBinding
		SchemaInitialization(@DefaultValue("false") boolean enabled) {
			this.enabled = enabled;
		}

		boolean isEnabled() {
			return enabled;
		}
	}
}
