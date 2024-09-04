/*
 * Copyright 2024 the original author or authors.
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

import contributed.ApplicationModuleSourceContribution;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Unit tests for {@link ApplicationModuleSourceContributions}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class ApplicationModuleSourceContributionsUnitTests {

	@Test // GH-613
	void detectsContributions() {

		var factories = List.of(new ApplicationModuleSourceContribution());
		var importer = new ClassFileImporter().withImportOption(new ImportOption.OnlyIncludeTests());
		var strategy = ApplicationModuleDetectionStrategy.directSubPackage();

		var contributions = new ApplicationModuleSourceContributions(factories, importer::importPackages, strategy, false);

		assertThat(contributions.getRootPackages()).contains("contributed");
		assertThat(contributions.getSources())
				.extracting(ApplicationModuleSource::getModuleBasePackage)
				.extracting(JavaPackage::getName)
				.containsExactlyInAnyOrder("contributed.detected", "contributed.enumerated");
	}
}
