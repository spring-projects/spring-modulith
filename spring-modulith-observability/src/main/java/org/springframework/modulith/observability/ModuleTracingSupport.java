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

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.util.Assert;

/**
 * @author Oliver Drotbohm
 */
class ModuleTracingSupport implements BeanClassLoaderAware {

	private final Supplier<ApplicationModules> modules;
	private final ApplicationRuntime context;
	private ClassLoader classLoader;

	protected ModuleTracingSupport(ApplicationRuntime context) {

		Assert.notNull(context, "ApplicationContext must not be null!");

		this.modules = ModulesRuntime.of(context);
		this.context = context;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	protected final ApplicationModules getModules() {

		try {
			return modules.get();
		} catch (Exception o_O) {
			throw new RuntimeException(o_O);
		}
	}

	protected final Class<?> getBeanUserClass(Object bean, String beanName) {
		return context.getUserClass(bean, beanName);
	}

	protected final Object addAdvisor(Object bean, Advisor advisor) {
		return addAdvisor(bean, advisor, __ -> {});
	}

	protected final Object addAdvisor(Object bean, Advisor advisor, Consumer<ProxyFactory> customizer) {

		if (Advised.class.isInstance(bean)) {

			((Advised) bean).addAdvisor(0, advisor);
			return bean;

		} else {

			ProxyFactory factory = new ProxyFactory(bean);
			customizer.accept(factory);
			factory.addAdvisor(advisor);

			return factory.getProxy(classLoader);
		}
	}
}
