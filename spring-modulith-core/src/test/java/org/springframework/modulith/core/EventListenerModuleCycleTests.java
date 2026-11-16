/*
 * Copyright 2026 the original author or authors.
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

class EventListenerModuleCycleTests {

	@Test
	void doesNotConsiderEventListenerSignaturesForCycleDetection() {

		var modules = TestUtils.of("reproducers.gh1981");

		assertThat(modules.detectViolations().getMessages()).isEmpty();

		var catalog = modules.getModuleByName("catalog").orElseThrow();
		var payments = modules.getModuleByName("payments").orElseThrow();

		assertThat(catalog.getDependencies(modules, DependencyDepth.IMMEDIATE).uniqueModules()).contains(payments);
		assertThat(payments.getDependencies(modules, DependencyDepth.IMMEDIATE).uniqueModules()).contains(catalog);
	}

	@Test
	void stillDetectsCyclesBetweenSynchronousModuleDependencies() {

		var modules = TestUtils.of("example.cycle");

		assertThat(modules.detectViolations().getMessages())
				.anySatisfy(it -> assertThat(it).containsIgnoringCase("cycle"));
	}
}
