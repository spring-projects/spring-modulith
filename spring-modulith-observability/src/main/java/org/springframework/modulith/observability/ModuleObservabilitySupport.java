/*
 * Copyright 2022-2024 the original author or authors.
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

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;

/**
 * @author Oliver Drotbohm
 */
class ModuleObservabilitySupport implements BeanClassLoaderAware {

	private ClassLoader classLoader;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	protected final Object addAdvisor(Object bean, Advisor advisor) {
		return addAdvisor(bean, advisor, __ -> {});
	}

	protected final Object addAdvisor(Object bean, Advisor advisor, Consumer<ProxyFactory> customizer) {

		if (bean instanceof Advised advised) {

			advised.addAdvisor(asyncAdvisorIndex(advised) + 1, advisor);

			return bean;

		} else {

			ProxyFactory factory = new ProxyFactory(bean);
			customizer.accept(factory);
			factory.addAdvisor(advisor);

			return factory.getProxy(classLoader);
		}
	}

	private static int asyncAdvisorIndex(Advised advised) {

		Advisor[] advisors = advised.getAdvisors();

		for (int i = 0; i < advised.getAdvisorCount(); i++) {

			if (advisors[i] instanceof AsyncAnnotationAdvisor) {
				return i;
			}
		}

		return -1;
	}
}
