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
package org.springframework.modulith.core;

import static org.assertj.core.api.Assertions.*;

import example.Example;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApplicationModuleSource}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class ApplicationModuleSourceUnitTests {

	@Test // GH-846
	void detectsSources() {

		var rootPackage = TestUtils.getPackage(Example.class);
		var detectionStrategy = ApplicationModuleDetectionStrategy.directSubPackage();

		var sources = ApplicationModuleSource.from(rootPackage, detectionStrategy, false);

		assertThat(sources)
				.extracting(ApplicationModuleSource::getIdentifier)
				.extracting(ApplicationModuleIdentifier::toString)
				.contains("ninvalid", "customId", "invalid", "ni", "ni.nested.b.first", "secondCustomized", "ni.nested");
	}

	@Test // GH-1042
	void doesNotPickUpIdFromNestedPackages() {

		var pkg = TestUtils.getPackage("reproducers.gh1042");

		var sources = ApplicationModuleSource.from(pkg, ApplicationModuleDetectionStrategy.directSubPackage(), false);

		assertThat(sources)
				.extracting(ApplicationModuleSource::getIdentifier)
				.extracting(ApplicationModuleIdentifier::toString)
				.contains("module", // picked from package name not from annotation in nested package
						"gh-1042-nested"); // from annotation in nested package
	}

	@Test // GH-1052
	void detectsApplicationModuleMetadataOnAnnotatedType() {

		var pkg = TestUtils.getPackage("reproducers");

		var sources = ApplicationModuleSource.from(pkg, root -> root.getSubPackage("gh1052").stream(), false);

		assertThat(sources).hasSize(1)
				.extracting(ApplicationModuleSource::getIdentifier)
				.extracting(ApplicationModuleIdentifier::toString)
				.containsExactly("on-marker-type");
	}
}
