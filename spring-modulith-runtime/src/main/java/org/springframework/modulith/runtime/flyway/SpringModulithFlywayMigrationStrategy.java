/*
 * Copyright 2025-2026 the original author or authors.
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

import java.util.List;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.util.Assert;

/**
 * A custom {@link FlywayMigrationStrategy} that customizes the Flyway execution to rather run multiple migrations for
 * each of the application modules identified by the configured {@link ApplicationModuleIdentifiers}. This customization
 * is only applied for already configured locations that do not use any wildcards.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
public class SpringModulithFlywayMigrationStrategy implements FlywayMigrationStrategy {

	private static final ApplicationModuleIdentifier ROOT = ApplicationModuleIdentifier.of("__root");
	private static final Logger LOGGER = LoggerFactory.getLogger(SpringModulithFlywayMigrationStrategy.class);

	private final ApplicationModuleIdentifiers identifiers;

	/**
	 * Creates a new {@link SpringModulithFlywayMigrationStrategy} for the given {@link ApplicationModuleIdentifiers}.
	 *
	 * @param identifiers must not be {@literal null}.
	 */
	public SpringModulithFlywayMigrationStrategy(ApplicationModuleIdentifiers identifiers) {

		Assert.notNull(identifiers, "ApplicationModuleIdentifiers must not be null!");

		this.identifiers = identifiers;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy#migrate(org.flywaydb.core.Flyway)
	 */
	@Override
	public void migrate(Flyway flyway) {

		new SpringModulithFlywayCustomizer(flyway)
				.augment(identifiers)
				.forEach(Flyway::migrate);
	}

	static class SpringModulithFlywayCustomizer {

		private final Flyway flyway;

		SpringModulithFlywayCustomizer(Flyway flyway) {
			this.flyway = flyway;
		}

		Stream<Flyway> augment(ApplicationModuleIdentifiers identifiers) {

			var configuration = flyway.getConfiguration();
			return Stream.concat(Stream.of(ROOT), identifiers.stream())
					.peek(it -> LOGGER.debug("Executing Flyway migrations for application module {}.", it))
					.map(it -> augmentWithApplicationModule(it, configuration));
		}

		private Flyway augmentWithApplicationModule(ApplicationModuleIdentifier identifier,
				Configuration configuration) {

			var locations = Stream.of(configuration.getLocations())
					.map(Location::toString)
					.map(it -> customizeLocation(it, identifier))
					.toList();

			return withNewLocation(configuration, locations, identifier);
		}

		private static String customizeLocation(String location, ApplicationModuleIdentifier identifier) {

			if (location.endsWith("*")) {
				return location;
			}

			var asPath = identifier.toString().replace('.', '/');

			return location + "/" + asPath;
		}

		private static Flyway withNewLocation(Configuration configuration, List<String> locations,
				ApplicationModuleIdentifier identifier) {

			var table = configuration.getTable();
			var customizedTable = identifier.equals(ROOT) ? table : table + "_" + identifier;

			return Flyway.configure()
					.configuration(configuration)
					.locations(locations.toArray(String[]::new))
					.table(customizedTable)
					.baselineVersion("0")
					.baselineOnMigrate(true)
					.load();
		}
	}
}
