/*
 * Copyright 2020-2023 the original author or authors.
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
package org.springframework.modulith.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Unit tests for {@link ApplicationModuleDetectionStrategy}.
 *
 * @author Oliver Drotbohm
 */
class ModuleDetectionStrategyUnitTest {

	@Test
	void usesExplicitlyAnnotatedConstant() {

		assertThat(ApplicationModuleDetectionStrategy.explictlyAnnotated())
				.isEqualTo(ApplicationModuleDetectionStrategies.EXPLICITLY_ANNOTATED);
	}

	@Test
	void usesDirectSubPackages() {

		assertThat(ApplicationModuleDetectionStrategy.directSubPackage())
				.isEqualTo(ApplicationModuleDetectionStrategies.DIRECT_SUB_PACKAGES);
	}

	@Test
	void detectsJMoleculesAnnotatedModule() {

		var classes = new ClassFileImporter() //
				.withImportOption(new ImportOption.OnlyIncludeTests()) //
				.importPackages("jmolecules");

		var javaPackage = JavaPackage.of(Classes.of(classes), "jmolecules");

		assertThat(ApplicationModuleDetectionStrategy.explictlyAnnotated().getModuleBasePackages(javaPackage))
				.containsExactly(javaPackage);
	}
}
