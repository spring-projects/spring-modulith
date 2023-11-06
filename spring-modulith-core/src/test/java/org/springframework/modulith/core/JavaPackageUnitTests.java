/*
 * Copyright 2019-2023 the original author or authors.
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

import example.ni.nested.a.InNestedA;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * @author Oliver Drotbohm
 */
class JavaPackageUnitTests {

	static final JavaClasses ALL_CLASSES = new ClassFileImporter() //
			.withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
			.importPackages("example");

	Classes classes = Classes.of(ALL_CLASSES);
	JavaPackage pkg = JavaPackage.of(classes, "example.ni");

	@Test
	void detectsDirectSubPackages() throws Exception {

		assertThat(pkg.getLocalName()).isEqualTo("ni");
		assertThat(pkg.getDirectSubPackages()) //
				.extracting(JavaPackage::getLocalName) //
				.containsExactlyInAnyOrder("api", "internal", "nested", "ontype", "spi");
	}

	@Test
	void detectsAllSubPackages() {

		var subPackages = pkg.getSubPackages();

		assertThat(subPackages.stream()
				.map(JavaPackage::getPackageName)
				.map(it -> it.getLocalName("example.ni")))
						.containsExactly(
								"api",
								"internal",
								"nested",
								"nested.a",
								"nested.b",
								"nested.b.first",
								"nested.b.second",
								"ontype",
								"spi");
	}

	@Test
	void detectsFlattenedSubPackages() {

		var subPackages = pkg.getSubPackages();

		assertThat(subPackages.flatten().stream()
				.map(JavaPackage::getPackageName)
				.map(it -> it.getLocalName("example.ni")))
						.containsExactlyInAnyOrder("api", "internal", "nested", "ontype", "spi");
	}

	@Test
	void considersExclusionsForClassesLookup() {

		assertThat(pkg.contains(InNestedA.class.getName()));

		var nestedA = JavaPackage.of(classes, "example.ni.nested.a");
		assertThat(nestedA.contains(InNestedA.class.getName()));

		assertThat(pkg.getClasses(List.of(nestedA)).stream().map(JavaClass::getName))
				.doesNotContain(InNestedA.class.getName());
	}

	@Test
	void detectsSubPackage() {

		var classes = Classes.of(ALL_CLASSES);
		var root = JavaPackage.of(classes, "example.ni");
		var direct = JavaPackage.of(classes, "example.ni.internal");
		var nested = JavaPackage.of(classes, "example.ni.internal.nested");

		assertThat(direct.isSubPackageOf(root)).isTrue();
		assertThat(nested.isSubPackageOf(root)).isTrue();
		assertThat(nested.isSubPackageOf(direct)).isTrue();
	}

	@Test
	void samePackagesConsideredEqual() {

		var first = JavaPackage.of(classes, "example.ni");
		var second = JavaPackage.of(classes, "example.ni");

		assertThat(first.equals(second)).isTrue();
		assertThat(second.equals(first)).isTrue();
	}
}
