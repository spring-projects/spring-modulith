/*
 * Copyright 2019-2026 the original author or authors.
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
import org.springframework.modulith.ApplicationModule;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * @author Oliver Drotbohm
 */
class JavaPackageUnitTests {

	static final JavaClasses ALL_CLASSES = new ClassFileImporter() //
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) //
			.importPackages("com.acme.myproject");

	static final JavaClasses PKG_CLASSES = new ClassFileImporter() //
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) //
			.importPackages("pkg");

	@Test
	void detectsDirectSubPackages() throws Exception {

		Classes classes = Classes.of(ALL_CLASSES);
		JavaPackage pkg = JavaPackage.of(classes, "com.acme.myproject.complex");

		assertThat(pkg.getLocalName()).isEqualTo("complex");
		assertThat(pkg.getDirectSubPackages()) //
				.extracting(JavaPackage::getLocalName) //
				.contains("api", "internal", "spi");
	}

	@Test // GH-522
	void findsAnnotationOnPackageInfo() {

		var annotation = JavaPackage.of(Classes.of(PKG_CLASSES), "pkg.onpackage") //
				.findAnnotation(ApplicationModule.class);

		assertThat(annotation).hasValueSatisfying(it -> {
			assertThat(it.displayName()).isEqualTo("onPackage");
		});
	}

	@Test // GH-522
	void findsAnnotationOnPackageType() {

		var annotation = JavaPackage.of(Classes.of(PKG_CLASSES), "pkg.ontype") //
				.findAnnotation(ApplicationModule.class);

		assertThat(annotation).hasValueSatisfying(it -> {
			assertThat(it.displayName()).isEqualTo("onType");
		});
	}

	@Test // GH-522
	void rejectsMultipleAnnotationsOnType() {

		assertThatIllegalStateException().isThrownBy(() -> {
			JavaPackage.of(Classes.of(PKG_CLASSES), "pkg.multipleontype") //
					.findAnnotation(ApplicationModule.class);
		});
	}
}
