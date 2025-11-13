/*
 * Copyright 2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.NamedExecutable;
import org.junit.jupiter.api.TestFactory;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy.SpringModulithFlywayCustomizer;

/**
 * Unit tests for {@link SpringModulithFlywayCustomizer}.
 *
 * @author Oliver Drotbohm
 */
public class SpringModulithFlywayCustomizerUnitTests {

	@TestFactory // GH-1067
	Stream<DynamicTest> augmentsLocationWithApplicationModuleIdentifier() {

		var expected = List.of(
				List.of("classpath:db/migration/first"),
				List.of("classpath:db/migration/second"));

		var twoIdentifiers = List.of("first", "second");
		var singleIdentifier = List.of("identifier");
		var fullyQualifiedIdentifier = List.of("fully.qualified");

		var noLocation = Collections.<String> emptyList();
		var wildcardLocation = List.of("classpath:db/migration/**");
		var multipleLocations = List.of("classpath:db/first", "classpath:db/second");
		var mixedLocation = List.of("classpath:db/foo", "classpath:db/migration/**");

		var first = new Fixture(twoIdentifiers, noLocation, expected);
		var second = new Fixture(singleIdentifier, wildcardLocation,
				List.of(List.of("classpath:db/migration/**")));
		var third = new Fixture(singleIdentifier, multipleLocations,
				List.of(List.of("classpath:db/first/identifier", "classpath:db/second/identifier")));
		var fourth = new Fixture(singleIdentifier, mixedLocation,
				List.of(List.of("classpath:db/foo/identifier", "classpath:db/migration/**")));
		var fifth = new Fixture(fullyQualifiedIdentifier, noLocation,
				List.of(List.of("classpath:db/migration/fully/qualified")));

		return DynamicTest.stream(Stream.of(first, second, third, fourth, fifth));
	}

	@Builder
	@ToString
	@RequiredArgsConstructor
	static class Fixture implements NamedExecutable {

		private final List<String> identifiers;
		private final List<String> locations;
		private final List<List<String>> expected;

		/*
		 * (non-Javadoc)
		 * @see org.junit.jupiter.api.function.Executable#execute()
		 */
		@Override
		public void execute() throws Throwable {

			var configure = Flyway.configure();

			if (!locations.isEmpty()) {
				configure = configure.locations(locations.toArray(String[]::new));
			}

			var result = new SpringModulithFlywayCustomizer(configure.load())
					.augment(createIdentifiers(identifiers));

			var numberOfFlywayInstances = identifiers.size() + 1;
			var expectedRootLocations = locations.isEmpty()
					? List.of("classpath:db/migration/__root")
					: locations.stream()
							.map(it -> it.endsWith("*") ? it : it + "/__root")
							.toList();

			assertThat(result)
					.hasSize(numberOfFlywayInstances)
					.satisfies(it -> {

						// Keeps original migration first

						assertThat(it).element(0)
								.extracting(Flyway::getConfiguration)
								.extracting(Configuration::getLocations, as(InstanceOfAssertFactories.ARRAY))
								.extracting(Object::toString)
								.containsAll(expectedRootLocations);

						// Has added expected additional migrations

						for (int i = 0; i < expected.size(); i++) {

							var customized = it.get(i + 1);

							assertThat(customized.getConfiguration().getLocations())
									.extracting(Location::toString)
									.containsAll(expected.get(i));
						}
					});
		}

		private static ApplicationModuleIdentifiers createIdentifiers(List<String> identifiers) {
			return ApplicationModuleIdentifiers.of(identifiers.stream().map(ApplicationModuleIdentifier::of).toList());
		}
	}
}
