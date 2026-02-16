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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

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

	private static MethodInvocation forMethod(String name, Class<?>... parameterTypes) throws Exception {

		var method = ObservedComponent.class.getDeclaredMethod(name, parameterTypes);

		return new MethodInvocation() {

			@Override
			public Object proceed() throws Throwable {
				return null;
			}

			@Override
			public Object getThis() {
				return null;
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
