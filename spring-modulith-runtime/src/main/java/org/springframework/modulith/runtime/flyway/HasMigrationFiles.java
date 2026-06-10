/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.runtime.flyway;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.util.Assert;

/**
 * A {@link MigrationFilter} that checks whether an {@link org.springframework.modulith.ApplicationModule} declares any
 * migration files at all.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
public class HasMigrationFiles implements MigrationFilter {

	private final ResourcePatternResolver resolver;

	/**
	 * Creates a new {@link HasMigrationFiles} {@link MigrationFilter}.
	 *
	 * @param resolver must not be {@literal null}.
	 */
	public HasMigrationFiles(ResourcePatternResolver resolver) {

		Assert.notNull(resolver, "ResourcePatternResolver must not be null!");

		this.resolver = resolver;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.runtime.flyway.MigrationFilter#shouldMigrate(org.springframework.modulith.core.ApplicationModuleIdentifier, org.flywaydb.core.Flyway)
	 */
	@Override
	public boolean shouldMigrate(ApplicationModuleIdentifier identifier, Flyway flyway) {

		var configuration = flyway.getConfiguration();

		return Arrays.stream(configuration.getLocations())
				.map(location -> getNumberOfMigrationFiles(location, identifier))
				.reduce(0, Integer::sum) > 0;
	}

	private int getNumberOfMigrationFiles(Location location, ApplicationModuleIdentifier identifier) {

		try {
			return resolver.getResources("%s/%s/*".formatted(location, identifier.toString())).length;
		} catch (FileNotFoundException e) {
			return 0;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
