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
package org.springframework.modulith.runtime.autoconfigure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;

import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Integration test for {@link SpringModulithRuntimeAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 * @author Michael Weirauch
 */
class SpringModulithRuntimeAutoConfigurationIntegrationTests {

	@SpringBootApplication
	static class SampleApp {}

	@Test // GH-87
	void bootstrapRegistersRuntimeInstances() {

		new ApplicationContextRunner()
				.withUserConfiguration(SampleApp.class)
				.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class))
				.run(context -> {
					assertThat(context.getBean(ApplicationRuntime.class)).isNotNull();
					assertThat(context.getBean(ApplicationModulesRuntime.class)).isNotNull();
				});
	}

	@Test // GH-160
	void missingArchUnitRuntimeDependencyEscalatesOnContextStartup() {

		new ApplicationContextRunner()
				.withUserConfiguration(SampleApp.class)
				.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class))
				.withClassLoader(new FilteredClassLoader(ClassFileImporter.class))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure().getCause())
							.isInstanceOf(BeanInstantiationException.class)
							.cause().isInstanceOf(MissingRuntimeDependency.class);
				});
	}

	@Test // GH-375
	void registersInitializingListenerIfInitializersPresent() {

		var tracker = new InitializationTracker();

		var beanName = "applicationModuleInitializingListener";
		var runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class))
				.withBean(InitializationTracker.class, () -> tracker);

		// No initializer -> no listener
		runner.run(context -> {

			assertThat(context).doesNotHaveBean(beanName);
			tracker.assertBeanNotInitialized(ApplicationModulesRuntime.class);
		});

		// Initializer -> listener
		runner.withBean(ApplicationModuleInitializer.class, () -> () -> {})
				.withBean(DummyApplication.class)
				.run(context -> {

					context.publishEvent(mock(ApplicationStartedEvent.class));
					assertThat(context).hasBean(beanName);
				});
	}

	static class InitializationTracker implements BeanPostProcessor, BeanFactoryAware {

		private final Map<String, Class<?>> initializedBeans = new HashMap<>();
		private ListableBeanFactory beanFactory;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.BeanFactoryAware#setBeanFactory(org.springframework.beans.factory.BeanFactory)
		 */
		@Override
		public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
			this.beanFactory = (ListableBeanFactory) beanFactory;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization(java.lang.Object, java.lang.String)
		 */
		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

			initializedBeans.put(beanName, AopUtils.getTargetClass(bean));

			return bean;
		}

		public void assertBeanInitialized(Class<?> type) {
			assertThat(initializedBeans).containsValue(type);
		}

		public void assertBeanNotInitialized(Class<?> type) {

			var names = beanFactory.getBeanNamesForType(type);

			assertThat(names).isNotEmpty();
			assertThat(initializedBeans).doesNotContainKeys(names);
		}
	}

	@SpringBootApplication
	static class DummyApplication {}
}
