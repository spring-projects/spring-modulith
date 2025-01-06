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

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Unit tests for {@link ApplicationModuleInformation}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModuleInformationUnitTests {

	static final JavaClasses PKG_CLASSES = new ClassFileImporter() //
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) //
			.importPackages("pkg");

	@Test // GH-522
	void detectsApplicationInformationOnType() {

		var pkg = JavaPackage.of(Classes.of(PKG_CLASSES), "pkg.ontype");
		var information = ApplicationModuleInformation.of(pkg);

		assertThat(information.getDisplayName()).hasValue("onType");
	}
}
