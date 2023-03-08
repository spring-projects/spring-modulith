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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.StreamUtils;

/**
 * Initializes the DB schema used to store events
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
class DatabaseSchemaInitializer implements InitializingBean {

	private final JdbcOperations jdbcOperations;
	private final ResourceLoader resourceLoader;
	private final DatabaseType databaseType;

	/**
	 * Creates a new {@link DatabaseSchemaInitializer} for the given {@link JdbcOperations}, {@link ResourceLoader} and
	 * {@link DatabaseType}.
	 *
	 * @param jdbcOperations must not be {@literal null}.
	 * @param resourceLoader must not be {@literal null}.
	 * @param databaseType must not be {@literal null}.
	 */
	public DatabaseSchemaInitializer(JdbcOperations jdbcOperations, ResourceLoader resourceLoader,
			DatabaseType databaseType) {

		this.jdbcOperations = jdbcOperations;
		this.resourceLoader = resourceLoader;
		this.databaseType = databaseType;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() {

		var schemaResourceFilename = databaseType.getSchemaResourceFilename();
		var schemaDdlResource = resourceLoader.getResource(ResourceLoader.CLASSPATH_URL_PREFIX + schemaResourceFilename);
		var schemaDdl = asString(schemaDdlResource);

		jdbcOperations.execute(schemaDdl);
	}

	private static String asString(Resource resource) {

		try {
			return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
