/*
 * Copyright 2023 the original author or authors.
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

/**
 * Unit tests for {@link ApplicationModules}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModulesUnitTests {

	ApplicationModules modules = TestUtils.of("example", "example.ninvalid");

	@Test
	void discoversComplexModuleArrangement() {

		assertThat(modules)
				.extracting(ApplicationModule::getName)
				.containsExactlyInAnyOrder("nested", "first", "second", "springbean", "ni", "jmolecules", "invalid");
	}

	@Test
	void detectsModuleNesting() {

		var ni = modules.getModuleByName("ni").orElseThrow();
		var nested = modules.getModuleByName("nested").orElseThrow();
		var inner = modules.getModuleByName("first").orElseThrow();

		assertThat(inner.getParentModule(modules)).hasValue(nested);
		assertThat(nested.getParentModule(modules)).hasValue(ni);
		assertThat(ni.getParentModule(modules)).isEmpty();

		assertThat(ni.getDirectlyNestedModules(modules))
				.extracting(ApplicationModule::getName)
				.containsExactlyInAnyOrder("nested");

		assertThat(ni.getNestedModules(modules))
				.extracting(ApplicationModule::getName)
				.containsExactlyInAnyOrder("nested", "first", "second");
	}

	@Test
	void detectsInvalidReferenceToNestedModule() {

		var violations = modules.detectViolations();
		var messages = violations.getMessages();

		assertThat(messages)
				.hasSize(3)
				.satisfiesExactlyInAnyOrder(
						it -> assertThat(it).contains("Invalid", "'invalid'", "'nested'"),
						it -> assertThat(it).contains("Invalid", "'invalid'", "'first'"),
						it -> assertThat(it).contains("Invalid", "'ni'", "'first'"));
	}
}
