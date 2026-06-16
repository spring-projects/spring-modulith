/*
 * Copyright 2024-2026 the original author or authors.
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
import example.sample.SampleComponent;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ArchitecturallyEvidentType;
import org.springframework.modulith.observability.ObservedModule;
import org.springframework.modulith.observability.support.DefaultObservedModule;
import org.springframework.modulith.test.TestApplicationModules;

/**
 * Unit tests for {@link DefaultObservedModule}.
 *
 * @author Oliver Drotbohm
 */
class DefaultObservedModuleUnitTests {

	static final ApplicationModules modules = TestApplicationModules.of("example");

	ApplicationModule module = modules.getModuleByName("sample").orElseThrow();
	ArchitecturallyEvidentType type = module.getArchitecturallyEvidentType(ObservedComponent.class);
	ObservedModule observedModule = new DefaultObservedModule(module);

	@Test // GH-927
	void detectsEventListenerInvocation() throws Exception {

		assertThat(observedModule.isEventListenerInvocation(forMethod("on", Object.class))).isTrue();
		assertThat(observedModule.isEventListenerInvocation(forMethod("someMethod"))).isFalse();
	}

	@Test // GH-1748
	void rendersUnboundedWildcardGenericWithoutException() throws Exception {

		var invocation = forMethod(new SampleComponent(), "withWildcard", List.class);

		assertThatNoException().isThrownBy(() -> observedModule.format(invocation));
		assertThat(observedModule.format(invocation)).contains("withWildcard(").contains("<?>");
	}

	@Test // GH-1748
	void rendersUnboundedTypeVariableGenericWithoutException() throws Exception {

		var invocation = forMethod(new SampleComponent(), "withTypeVariable", List.class);

		assertThatNoException().isThrownBy(() -> observedModule.format(invocation));
		assertThat(observedModule.format(invocation)).contains("withTypeVariable(").contains("<?>");
	}

	private static MethodInvocation forMethod(String name, Class<?>... parameterTypes) throws Exception {

		var method = ObservedComponent.class.getDeclaredMethod(name, parameterTypes);

		return forMethod(null, method);
	}

	private static MethodInvocation forMethod(Object target, String name, Class<?>... parameterTypes) throws Exception {

		var method = target.getClass().getDeclaredMethod(name, parameterTypes);

		return forMethod(target, method);
	}

	private static MethodInvocation forMethod(Object target, Method method) {

		return new MethodInvocation() {

			@Override
			public Object proceed() throws Throwable {
				return null;
			}

			@Override
			public Object getThis() {
				return target;
			}

			@Override
			public AccessibleObject getStaticPart() {
				return method;
			}

			@Override
			public Object[] getArguments() {
				return new Object[] {};
			}

			@Override
			public Method getMethod() {
				return method;
			}
		};
	}
}
