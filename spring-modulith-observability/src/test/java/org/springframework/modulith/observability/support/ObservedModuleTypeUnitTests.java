/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.observability.support;

import static org.assertj.core.api.Assertions.*;

import example.sample.ObservedComponent;
import example.sample.SampleConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ArchitecturallyEvidentType;
import org.springframework.modulith.core.Types;
import org.springframework.modulith.observability.support.DefaultObservedModule;
import org.springframework.modulith.observability.support.ObservedModuleType;
import org.springframework.modulith.test.TestApplicationModules;
import org.springframework.util.ReflectionUtils;

/**
 * Unit tests for {@link ObservedModuleType}.
 *
 * @author Oliver Drotbohm
 */
class ObservedModuleTypeUnitTests {

	static final ApplicationModules modules = TestApplicationModules.of("example");

	ApplicationModule module = modules.getModuleByName("sample").orElseThrow();
	ArchitecturallyEvidentType type = module.getArchitecturallyEvidentType(ObservedComponent.class);

	ObservedModuleType observedType = new ObservedModuleType(modules, new DefaultObservedModule(module), type);

	@Test // GH-106, GH-744
	void onlyExposesUserMethodsAsToBeIntercepted() {

		assertThat(observedType.getMethodsToIntercept()).satisfies(it -> {

			assertThat(it.test(ReflectionUtils.findMethod(ObservedComponent.class, "someMethod"))).isTrue();
			assertThat(it.test(ReflectionUtils.findMethod(ObservedComponent.class, "on", Object.class))).isTrue();

			assertThat(it.test(ReflectionUtils.findMethod(ObservedComponent.class, "someInternalMethod"))).isFalse();
			assertThat(it.test(ReflectionUtils.findMethod(Object.class, "toString"))).isFalse();
			assertThat(it.test(ReflectionUtils.findMethod(Advised.class, "getTargetClass"))).isFalse();
		});
	}

	@Test // GH-106
	void considersExposedTypeAsToBeIntercepted() {
		assertThat(observedType.shouldBeObserved()).isTrue();
	}

	@Test // GH-332
	void doesNotObserveConfigurationClasses() {

		var type = module.getArchitecturallyEvidentType(SampleConfiguration.class);
		var observedType = new ObservedModuleType(modules, new DefaultObservedModule(module), type);

		assertThat(observedType.shouldBeObserved()).isFalse();
	}

	@Test // GH-936
	void exposesMessageListenerMethodsForObservation() {

		var type = Types.loadIfPresent("example.sample.SampleMessageListener");

		var architecturallyEvidentType = module.getArchitecturallyEvidentType(type);
		var moduleType = new ObservedModuleType(modules, new DefaultObservedModule(module), architecturallyEvidentType);

		assertThat(moduleType.shouldBeObserved()).isTrue();
	}
}
