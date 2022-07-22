/*
 * Copyright 2022 the original author or authors.
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
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.StreamUtils;

/**
 * Initializes the DB schema used to store events
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
class DatabaseSchemaInitializer implements ResourceLoaderAware, InitializingBean {

	private final JdbcTemplate jdbcTemplate;

	private ResourceLoader resourceLoader;

	/**
	 * Creates a new {@link DatabaseSchemaInitializer} for the given {@link JdbcTemplate} and ini
	 *
	 * @param jdbcTemplate
	 * @param initEnabled
	 */
	public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {

		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void afterPropertiesSet() throws MetaDataAccessException {

		var fromDataSource = DatabaseDriver.fromDataSource(jdbcTemplate.getDataSource());
		var databaseName = fromDataSource.name().toLowerCase();
		var schemaDdlResource = resourceLoader.getResource("/schema-" + databaseName + ".sql");
		var schemaDdl = asString(schemaDdlResource);

		jdbcTemplate.execute(schemaDdl);
	}

	private String asString(Resource resource) {

		try {
			return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
