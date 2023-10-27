/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.modulith.core.config;

import java.util.Arrays;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.util.ClassUtils;

/**
 * A {@link BeanFactoryPostProcessor} verifying that the {@code spring-modulith-runtime} artifact is on the classpath in
 * case any beans implementing {@link ApplicationModuleInitializer} are found in the
 * {@code org.springframework.context.ApplicationContext}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
@AutoConfiguration
class ApplicationModuleInitializerRuntimeVerification implements BeanFactoryPostProcessor {

	private static final String ARTIFACT_MISSING = "Detected bean(s) (%s) implementing ApplicationModuleInitializer but Spring Modulith Runtime artifact not on the classpath! Please add org.springframework.modulith:spring-modulith-runtime to your project!";

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor#postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

		var initializerBeanNames = beanFactory.getBeanNamesForType(ApplicationModuleInitializer.class);

		if (initializerBeanNames.length == 0) {
			return;
		}

		if (!ClassUtils.isPresent("org.springframework.modulith.runtime.ApplicationRuntime", getClass().getClassLoader())) {
			throw new IllegalStateException(ARTIFACT_MISSING.formatted(Arrays.toString(initializerBeanNames)));
		}
	}
}
