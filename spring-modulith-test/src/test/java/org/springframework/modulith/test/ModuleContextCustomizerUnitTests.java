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
package org.springframework.modulith.test;

import static org.assertj.core.api.Assertions.*;

import example.module.SampleTestA;
import example.module.SampleTestB;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.test.ModuleContextCustomizerFactory.ModuleContextCustomizer;

/**
 * Unit tests for {@link ModuleTypeExcludeFilter}.
 *
 * @author Oliver Drotbohm
 */
class ModuleContextCustomizerUnitTests {

	@Test
	void instancesForSameTargetTypeAreEqual() {

		var left = new ModuleContextCustomizer(SampleTestA.class);
		var right = new ModuleContextCustomizer(SampleTestA.class);

		assertThat(left).isEqualTo(right);
		assertThat(right).isEqualTo(left);
		assertThat(left).hasSameHashCodeAs(right);
	}

	@Test // GH-1090
	void usesTestClassIfConfigured() {

		var customizer = new ModuleContextCustomizer(SampleTestA.class);
		var execution = customizer.getExecution();

		assertThat(execution.getModule())
				.extracting(ApplicationModule::getIdentifier)
				.extracting(ApplicationModuleIdentifier::toString)
				.isEqualTo("module");
	}

	@Test // GH-1050
	void instancesWithSameModuleSetupAreConsideredEqual() {

		var left = new ModuleContextCustomizer(SampleTestA.class);
		var right = new ModuleContextCustomizer(SampleTestB.class);

		assertThat(left).isEqualTo(right);
		assertThat(right).isEqualTo(left);
		assertThat(left).hasSameHashCodeAs(right);
	}
}
