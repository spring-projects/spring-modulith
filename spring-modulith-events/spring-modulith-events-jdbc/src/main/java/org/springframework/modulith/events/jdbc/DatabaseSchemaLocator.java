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

import java.util.Collection;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * Simple wrapper around a {@link ResourceLoader} to load database specific schema files from the classpath.
 *
 * @author Oliver Drotbohm
 */
public class DatabaseSchemaLocator {

	private final ResourceLoader resourceLoader;

	/**
	 * Creates a new {@link DatabaseSchemaLocator} for the given {@link ResourceLoader}.
	 *
	 * @param resourceLoader must not be {@literal null}.
	 */
	DatabaseSchemaLocator(ResourceLoader resourceLoader) {

		Assert.notNull(resourceLoader, "ResourceLoader must not be null!");

		this.resourceLoader = resourceLoader;
	}

	/**
	 * Loads the {@link Resource} containing the schema for the given {@link JdbcRepositorySettings} from the classpath.
	 *
	 * @param settings must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Collection<Resource> getSchemaResource(JdbcRepositorySettings settings) {

		Assert.notNull(settings, "JdbcRepositorySettings must not be null!");

		var schemaResourceFilename = settings.getSchemaResourceFilename();
		var schemaResource = resourceLoader.getResource(ResourceLoader.CLASSPATH_URL_PREFIX + schemaResourceFilename);

		return !settings.isArchiveCompletion()
				? List.of(schemaResource)
				: List.of(schemaResource, resourceLoader
						.getResource(ResourceLoader.CLASSPATH_URL_PREFIX + settings.getArchiveSchemaResourceFilename()));

	}
}
