/*
 * Copyright 2018-2022 the original author or authors.
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
package com.acme.myproject;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.model.Modules;
import org.springframework.modulith.model.Modules.Filters;
import org.springframework.modulith.model.Violations;

import com.acme.myproject.invalid.InvalidComponent;
import com.acme.myproject.moduleB.internal.InternalComponentB;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Test cases to verify the validity of the overall modulith rules
 *
 * @author Oliver Drotbohm
 * @author Peter Gafert
 */
class ModulithTest {

	static final DescribedPredicate<JavaClass> DEFAULT_EXCLUSIONS = Filters.withoutModules("cycleA", "cycleB", "invalid2",
			"fieldinjected");

	@Test
	void verifyModules() {

		String componentName = InternalComponentB.class.getSimpleName();

		assertThatExceptionOfType(Violations.class) //
				.isThrownBy(() -> Modules.of(Application.class, DEFAULT_EXCLUSIONS).verify()) //
				.withMessageContaining(String.format("Module '%s' depends on non-exposed type %s within module 'moduleB'",
						"invalid", InternalComponentB.class.getName()))
				.withMessageContaining(String.format("%s declares constructor %s(%s)", InvalidComponent.class.getSimpleName(),
						InvalidComponent.class.getSimpleName(), componentName));
	}

	@Test
	void verifyModulesWithoutInvalid() {
		Modules.of(Application.class, DEFAULT_EXCLUSIONS.or(Filters.withoutModule("invalid"))).verify();
	}

	@Test
	void detectsCycleBetweenModules() {

		assertThatExceptionOfType(Violations.class) //
				.isThrownBy(() -> Modules.of(Application.class, Filters.withoutModules("invalid", "invalid2")).verify()) //

				// mentions modules
				.withMessageContaining("cycleA") //
				.withMessageContaining("cycleB") //

				// mentions offending types
				.withMessageContaining("CycleA") //
				.withMessageContaining("CycleB");
	}
}
