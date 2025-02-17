/*
 * Copyright 2022-2025 the original author or authors.
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

import java.lang.reflect.Method;
import java.util.Arrays;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ArchitecturallyEvidentType.ReferenceMethod;
import org.springframework.modulith.core.FormattableType;
import org.springframework.modulith.core.SpringBean;
import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

class DefaultObservedModule implements ObservedModule {

	private final ApplicationModule module;

	/**
	 * Creates a new {@link DefaultObservedModule} for the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 */
	DefaultObservedModule(ApplicationModule module) {

		Assert.notNull(module, "ApplicationModule must not be null!");

		this.module = module;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#getName()
	 */
	@Override
	public String getName() {
		return getIdentifier().toString();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#getIdentifier()
	 */
	@Override
	public ApplicationModuleIdentifier getIdentifier() {
		return module.getIdentifier();
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
		return toString(findModuleLocalMethod(invocation), module);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#exposes(com.tngtech.archunit.core.domain.JavaClass)
	 */
	@Override
	public boolean exposes(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return module.isExposed(type);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#isObservedModule(org.springframework.modulith.model.Module)
	 */
	@Override
	public boolean isObservedModule(ApplicationModule module) {

		Assert.notNull(module, "AppliucationModule must not be null!");

		return this.module.equals(module);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.support.ObservedModule#getObservedModuleType(java.lang.Class, org.springframework.modulith.core.ApplicationModules)
	 */
	public @Nullable ObservedModuleType getObservedModuleType(Class<?> type, ApplicationModules modules) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(modules, "ApplicationModules must not be null!");

		return module.getSpringBeans().stream()
				.filter(it -> it.getFullyQualifiedTypeName().equals(type.getName()))
				.map(SpringBean::toArchitecturallyEvidentType)
				.findFirst()
				.map(it -> new ObservedModuleType(modules, this, it))
				.filter(ObservedModuleType::shouldBeObserved)
				.orElse(null);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ObservedModule#isEventListenerInvocation(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override
	public boolean isEventListenerInvocation(MethodInvocation invocation) {

		var method = findModuleLocalMethod(invocation);
		var type = module.getArchitecturallyEvidentType(method.getDeclaringClass());

		return type.isEventListener()
				&& type.getReferenceMethods()
						.map(ReferenceMethod::getMethod)
						.map(JavaMethod::reflect)
						.anyMatch(method::equals);
	}

	private Method findModuleLocalMethod(MethodInvocation invocation) {

		Method method = invocation.getMethod();

		if (module.contains(method.getDeclaringClass())) {
			return invocation.getMethod();
		}

		if (!ProxyMethodInvocation.class.isInstance(invocation)) {
			return invocation.getMethod();
		}

		// For class-based proxies, use the target class

		var advised = (Advised) ((ProxyMethodInvocation) invocation).getProxy();
		var targetClass = advised.getTargetClass();

		if (targetClass != null && module.contains(targetClass)) {
			return AopUtils.getMostSpecificMethod(method, targetClass);
		}

		// For JDK proxies, find original interface the method was logically declared on

		for (Class<?> type : advised.getProxiedInterfaces()) {
			if (module.contains(type)) {
				if (Arrays.asList(type.getMethods()).contains(method)) {
					return AopUtils.getMostSpecificMethod(method, targetClass);
				}
			}
		}

		return invocation.getMethod();
	}

	private static String toString(Method method, ApplicationModule module) {

		var type = method.getDeclaringClass();

		var typeName = module.getType(type.getName())
				.map(FormattableType::of)
				.map(FormattableType::getAbbreviatedFullName)
				.orElseGet(() -> type.getName());

		return typeName + "." + method.getName() + "(…)";
	}
}
