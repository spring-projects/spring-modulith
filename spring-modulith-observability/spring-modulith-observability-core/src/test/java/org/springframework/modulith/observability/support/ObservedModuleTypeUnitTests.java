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
import example.sample.internal.AbstractSampleServiceImpl;
import example.sample.internal.InternalOnlyComponent;
import example.sample.internal.SampleServiceImpl;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Types;
import org.springframework.modulith.observability.ObservedModuleType;
import org.springframework.modulith.test.TestApplicationModules;
import org.springframework.util.ReflectionUtils;

/**
 * Unit tests for {@link ObservedModuleType}.
 *
 * @author Oliver Drotbohm
 */
class ObservedModuleTypeUnitTests {

	static final ApplicationModules MODULES = TestApplicationModules.of("example");
	static final ApplicationModule MODULE = MODULES.getModuleByName("sample").orElseThrow();

	ObservedModuleType observedType = observedTypeOf(ObservedComponent.class);

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
		assertThat(observedTypeOf(SampleConfiguration.class).shouldBeObserved()).isFalse();
	}

	@Test // GH-936
	void exposesMessageListenerMethodsForObservation() {

		Class<?> type = Types.loadIfPresent("example.sample.SampleMessageListener");

		assertThat(type).isNotNull();
		assertThat(observedTypeOf(type).shouldBeObserved()).isTrue();
	}

	@Test // GH-1712
	void considersInternalImplementationOfExposedInterfaceAsToBeIntercepted() {
		assertThat(observedTypeOf(SampleServiceImpl.class).shouldBeObserved()).isTrue();
	}

	@Test // GH-1712
	void considersInternalSubclassOfExposedAbstractClassAsToBeIntercepted() {
		assertThat(observedTypeOf(AbstractSampleServiceImpl.class).shouldBeObserved()).isTrue();
	}

	@Test // GH-1712
	void doesNotConsiderPurelyInternalComponentToBeIntercepted() {
		assertThat(observedTypeOf(InternalOnlyComponent.class).shouldBeObserved()).isFalse();
	}

	private static ObservedModuleType observedTypeOf(Class<?> type) {

		var evidentType = MODULE.getArchitecturallyEvidentType(type);

		return new ObservedModuleType(MODULES, new DefaultObservedModule(MODULE), evidentType);
	}
}
