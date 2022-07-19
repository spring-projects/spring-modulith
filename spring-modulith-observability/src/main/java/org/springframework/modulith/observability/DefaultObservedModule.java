/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.modulith.observability;

import lombok.RequiredArgsConstructor;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.Advised;
import org.springframework.modulith.model.FormatableJavaClass;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.model.SpringBean;

import com.tngtech.archunit.core.domain.JavaClass;

@RequiredArgsConstructor
class DefaultObservedModule implements ObservedModule {

	private final ApplicationModule module;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#getName()
	 */
	@Override
	public String getName() {
		return module.getName();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return module.getDisplayName();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#getInvokedMethod(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override
	public String getInvokedMethod(MethodInvocation invocation) {

		Method method = invocation.getMethod();

		if (module.contains(method.getDeclaringClass())) {
			return toString(invocation.getMethod(), module);
		}

		if (!ProxyMethodInvocation.class.isInstance(invocation)) {
			return toString(invocation.getMethod(), module);
		}

		// For class-based proxies, use the target class

		Advised advised = (Advised) ((ProxyMethodInvocation) invocation).getProxy();
		Class<?> targetClass = advised.getTargetClass();

		if (module.contains(targetClass)) {
			return toString(targetClass, method, module);
		}

		// For JDK proxies, find original interface the method was logically declared on

		for (Class<?> type : advised.getProxiedInterfaces()) {
			if (module.contains(type)) {
				if (Arrays.asList(type.getMethods()).contains(method)) {
					return toString(type, method, module);
				}
			}
		}

		return toString(invocation.getMethod(), module);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#exposes(com.tngtech.archunit.core.domain.JavaClass)
	 */
	@Override
	public boolean exposes(JavaClass type) {
		return module.isExposed(type);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#isObservedModule(org.springframework.modulith.model.Module)
	 */
	@Override
	public boolean isObservedModule(ApplicationModule module) {
		return this.module.equals(module);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#getInterceptionConfiguration(java.lang.Class, org.springframework.modulith.model.Modules)
	 */
	public ObservedModuleType getObservedModuleType(Class<?> type, ApplicationModules modules) {

		return module.getSpringBeans().stream()
				.filter(it -> it.getFullyQualifiedTypeName().equals(type.getName()))
				.map(SpringBean::toArchitecturallyEvidentType)
				.findFirst()
				.map(it -> new ObservedModuleType(modules, this, it))
				.filter(ObservedModuleType::shouldBeTraced)
				.orElse(null);
	}

	private static String toString(Method method, ApplicationModule module) {
		return toString(method.getDeclaringClass(), method, module);
	}

	private static String toString(Class<?> type, Method method, ApplicationModule module) {

		String typeName = module.getType(type.getName())
				.map(FormatableJavaClass::of)
				.map(FormatableJavaClass::getAbbreviatedFullName)
				.orElseGet(() -> type.getName());

		return String.format("%s.%s(…)", typeName, method.getName());
	}
}
