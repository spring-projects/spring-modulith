/*
 * Copyright 2022-2023 the original author or authors.
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

import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.util.ClassUtils;

/**
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
class SpringBootApplicationRuntime implements ApplicationRuntime {

	private static final Map<String, Boolean> APPLICATION_CLASSES = new ConcurrentHashMap<>();

	private final ApplicationContext context;

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

		var mainBeanNames = context.getBeanNamesForAnnotation(SpringBootApplication.class);

		return context.getType(mainBeanNames[0]);
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

		return APPLICATION_CLASSES.computeIfAbsent(type.getName(),
				it -> {

					if (it.startsWith("org.springframework")) {
						return false;
					}

					return it.startsWith(getMainApplicationClass().getPackage().getName())
							|| AutoConfigurationPackages.get(context).stream().anyMatch(pkg -> it.startsWith(pkg));
				});
	}
}
