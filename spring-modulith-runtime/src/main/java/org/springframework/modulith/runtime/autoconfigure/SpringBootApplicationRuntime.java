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
package org.springframework.modulith.runtime.autoconfigure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@link ApplicationRuntime} implementation based on an {@link ApplicationContext} and a class that's annotated with
 * {@link SpringBootApplication}.
 *
 * @author Oliver Drotbohm
 */
class SpringBootApplicationRuntime implements ApplicationRuntime {

	private static final Map<String, Boolean> APPLICATION_CLASSES = new ConcurrentHashMap<>();

	private final ApplicationContext context;
	private Class<?> mainApplicationClass;
	private List<String> resolvedAutoConfigurationPackages;

	/**
	 * Creates a new {@link SpringBootApplicationRuntime} for the given {@link ApplicationContext}.
	 *
	 * @param context must not be {@literal null}.
	 */
	SpringBootApplicationRuntime(ApplicationContext context) {

		Assert.notNull(context, "ApplicationContext must not be null!");

		this.context = context;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ApplicationRuntime#getId()
	 */
	@Override
	public String getId() {
		return context.getId();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ApplicationRuntime#getApplicationClass()
	 */
	@Override
	public Class<?> getMainApplicationClass() {

		if (mainApplicationClass == null) {

			// Traverse BeanDefinitions manually to avoid factory beans to be inspected
			this.mainApplicationClass = Arrays.stream(context.getBeanDefinitionNames())
					.filter(it -> context.findAnnotationOnBean(it, SpringBootApplication.class, false) != null)
					.map(context::getType)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("Couldn't find a class annotated with @SpringBootApplication!"));
		}

		return mainApplicationClass;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ApplicationRuntime#getBeanUserClass(java.lang.Object, java.lang.String)
	 */
	@Override
	public Class<?> getUserClass(Object bean, String beanName) {

		var beanType = context.containsBean(beanName)
				? context.getType(beanName)
				: bean.getClass();

		return ClassUtils.getUserClass(beanType);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.observability.ApplicationRuntime#isApplicationClass(java.lang.Class)
	 */
	@Override
	public boolean isApplicationClass(Class<?> type) {

		var applicationClass = getMainApplicationClass();

		return APPLICATION_CLASSES.computeIfAbsent(type.getName(), it -> computeIsApplicationClass(it, applicationClass));
	}

	private boolean computeIsApplicationClass(String fqn, Class<?> applicationClass) {

		if (fqn.startsWith("org.springframework")) {
			return false;
		}

		return fqn.startsWith(applicationClass.getPackage().getName())
				|| getAutoConfigurationPackages().stream().anyMatch(pkg -> fqn.startsWith(pkg));
	}

	/**
	 * Looks up the auto configuration packages and caches them to prevent further lookups.
	 *
	 * @return will never be {@literal null}.
	 */
	private List<String> getAutoConfigurationPackages() {

		if (resolvedAutoConfigurationPackages == null) {
			this.resolvedAutoConfigurationPackages = AutoConfigurationPackages.get(context);
		}

		return resolvedAutoConfigurationPackages;
	}
}
