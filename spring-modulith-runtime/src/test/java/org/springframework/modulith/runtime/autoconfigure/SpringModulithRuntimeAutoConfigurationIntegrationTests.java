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

import example.moduleA.ModuleAType;

import java.time.LocalDateTime;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.core.util.ApplicationModulesExporter;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration.ApplicationModulesBootstrap;
import org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration.RuntimeApplicationModuleVerifier;

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

	InitializationTracker tracker = new InitializationTracker();

	ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class))
			.withBean(InitializationTracker.class, () -> tracker);

	ClassLoader withoutMetadata = new FilteredClassLoader(
			new ClassPathResource(ApplicationModulesExporter.DEFAULT_LOCATION));

	@Test // GH-87
	void bootstrapRegistersRuntimeInstances() {

		runner.withUserConfiguration(SampleApp.class)
				.run(context -> {
					assertThat(context).hasSingleBean(ApplicationRuntime.class);
					assertThat(context).hasSingleBean(ApplicationModulesRuntime.class);
				});
	}

	@Test // GH-160
	void missingArchUnitRuntimeDependencyEscalatesOnContextStartup() {

		runner.withUserConfiguration(SampleApp.class)
				.withClassLoader(new FilteredClassLoader(ClassFileImporter.class))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure().getCause())
							.isInstanceOf(BeanInstantiationException.class)
							.cause().isInstanceOf(MissingRuntimeDependency.class);
				});
	}

	@Test // GH-375
	void registersIntializerInvokerIfInitializersPresent() {

		var unlisted = new ModuleAType();
		var listed = new SampleInitializer();

		var runner = this.runner.withUserConfiguration(SampleApp.class)
				.withBean(ModuleAType.class, () -> unlisted)
				.withBean(SampleInitializer.class, () -> listed);

		runner.run(context -> {

			assertThat(context).hasSingleBean(PrecomputedApplicationModuleInitializerInvoker.class);

			context.publishEvent(mock(ApplicationStartedEvent.class));

			// Initializer invoked
			assertThat(unlisted.initialized).isNotNull();
			assertThat(listed.initialized).isNotNull();
			assertThat(listed.initialized).isBefore(unlisted.initialized);

			tracker.assertBeanNotInitialized(ApplicationModulesRuntime.class);
		});

		runner.withClassLoader(withoutMetadata)
				.run(context -> {

					assertThat(context).hasSingleBean(DefaultApplicationModuleInitializerInvoker.class);

					context.publishEvent(mock(ApplicationStartedEvent.class));

					tracker.assertBeanInitialized(ApplicationModulesRuntime.class);
				});
	}

	@Test // GH-375
	void doesNotRegisterIntializerInvokerIfInitializersPresent() {

		runner.run(context -> {

			assertThat(context).doesNotHaveBean(ApplicationModuleInitializerInvoker.class);

			tracker.assertBeanNotInitialized(ApplicationModulesRuntime.class);
		});
	}

	@Test // GH-1066
	void registersApplicationModuleIdentifiersWithoutInstantiatingApplicationModulesIfMetadataPresent() {

		runner.run(context -> {

			tracker.assertBeanNotInitialized(ApplicationModulesRuntime.class);
			assertThat(context).hasSingleBean(ApplicationModuleIdentifiers.class);

			assertThat(context.getBean(ApplicationModuleIdentifiers.class))
					.extracting(ApplicationModuleIdentifier::toString)
					.containsExactly("a", "b", "c");
		});

		runner.withUserConfiguration(SampleApp.class)
				.withClassLoader(withoutMetadata)
				.run(context -> {

					tracker.assertBeanInitialized(ApplicationModulesRuntime.class);
					assertThat(context).hasSingleBean(ApplicationModuleIdentifiers.class);

					assertThat(context.getBean(ApplicationModuleIdentifiers.class)).isEmpty();
				});
	}

	@Test // GH-1287
	void doesNotActivateVerifyingListenerByDefault() {

		runner.run(context -> {
			assertThat(context).hasSingleBean(SpringModulithRuntimeProperties.class);
			assertThat(context).doesNotHaveBean(RuntimeApplicationModuleVerifier.class);
			tracker.assertBeanNotInitialized(ApplicationModulesBootstrap.class);
		});
	}

	@Test // GH-1287
	void registersVerifyingApplicationListenerIfConfigured() {

		runner.withUserConfiguration(SampleApp.class)
				.withPropertyValues("spring.modulith.runtime.verification-enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(RuntimeApplicationModuleVerifier.class);
					tracker.assertBeanInitialized(ApplicationModulesBootstrap.class);
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

	static class SampleInitializer implements ApplicationModuleInitializer {

		LocalDateTime initialized;

		@Override
		public void initialize() {
			this.initialized = LocalDateTime.now();
		}
	}
}
