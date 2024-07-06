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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;

/**
 * Configuration properties for JDBC.
 *
 * @author Raed Ben Hamouda
 */
@ConfigurationProperties(prefix = "spring.modulith.events.jdbc")
class JdbcConfigurationProperties {

	private final boolean schemaInitializationEnabled;
	private final String schema;

	/**
	 * Creates a new {@link JdbcConfigurationProperties} instance.
	 *
	 * @param schemaInitializationEnabled whether to initialize the JDBC event publication schema. Defaults to {@literal false}.
	 * @param schema the schema name of event publication table, can be {@literal null}.
	 */
	@ConstructorBinding
	JdbcConfigurationProperties(@DefaultValue("false") boolean schemaInitializationEnabled, @Nullable String schema) {

		this.schemaInitializationEnabled = schemaInitializationEnabled;
		this.schema = schema;
	}

	/**
	 * Whether to initialize the JDBC event publication schema.
	 */
	public boolean isSchemaInitializationEnabled() {
		return schemaInitializationEnabled;
	}

	/**
	 * The name of the schema where the event publication table resides.
	 *
	 * @return can be {@literal null}.
	 */
	public String getSchema() {
		return schema;
	}
}
