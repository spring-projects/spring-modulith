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
package org.springframework.modulith.core;

import static org.assertj.core.api.Assertions.*;

import example.Example;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Unit tests for {@link Classes}.
 *
 * @author Oliver Drotbohm
 */
class ClassesUnitTests {

	@Test // GH-1098
	void filtersClassesByPackageName() {

		var classes = TestUtils.getClasses(Example.class);
		var nestedDirectly = classes.thatResideIn(PackageName.of("example.ni.nested"), false);

		assertThat(nestedDirectly)
				.extracting(JavaClass::getSimpleName)
				.contains("InNested")
				.doesNotContain("InNestedA");

		var nestedRecursive = classes.thatResideIn(PackageName.of("example.ni.nested"), true);

		assertThat(nestedRecursive)
				.extracting(JavaClass::getSimpleName)
				.contains("InNested", "InNestedA")
				.doesNotContain("ApiType");
	}
}
