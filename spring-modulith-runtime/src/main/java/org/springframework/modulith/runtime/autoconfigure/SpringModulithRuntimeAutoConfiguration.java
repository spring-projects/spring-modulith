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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ApplicationModulesFactory;
import org.springframework.modulith.core.FormatableType;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.util.Assert;
import org.springframework.util.function.ThrowingSupplier;

/**
 * Auto-configuration to register an {@link ApplicationRuntime}, a {@link ApplicationModulesRuntime} and an
 * {@link ApplicationListener} to invoke all {@link ApplicationModuleInitializer}s as Spring Bean.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
class SpringModulithRuntimeAutoConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpringModulithRuntimeAutoConfiguration.class);
	private static final AsyncTaskExecutor EXECUTOR = new SimpleAsyncTaskExecutor();

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean(ApplicationRuntime.class)
	static ApplicationRuntime modulithsApplicationRuntime(ApplicationContext context) {
		return ApplicationRuntime.of(context);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean
	static ApplicationModulesRuntime modulesRuntime(ApplicationRuntime runtime) {

		ThrowingSupplier<ApplicationModules> modules = () -> EXECUTOR
				.submit(() -> ApplicationModulesBootstrap.initializeApplicationModules(runtime.getMainApplicationClass()))
				.get();

		return new ApplicationModulesRuntime(modules, runtime);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnBean(ApplicationModuleInitializer.class)
	static ApplicationListener<ApplicationStartedEvent> applicationModuleInitializingListener(
			ObjectProvider<ApplicationModulesRuntime> runtime,
			ObjectProvider<ApplicationModuleInitializer> initializers) {

		return event -> {

			var modules = runtime.getObject().get();

			initializers.stream() //
					.sorted(modules.getComparator()) //
					.map(it -> LOGGER.isDebugEnabled() ? new LoggingApplicationModuleInitializerAdapter(it, modules) : it)
					.forEach(ApplicationModuleInitializer::initialize);
		};
	}

	private static class LoggingApplicationModuleInitializerAdapter implements ApplicationModuleInitializer {

		private static final Logger LOGGER = LoggerFactory.getLogger(LoggingApplicationModuleInitializerAdapter.class);

		private final ApplicationModuleInitializer delegate;
		private final ApplicationModules modules;

		/**
		 * Creates a new {@link LoggingApplicationModuleInitializerAdapter} for the given
		 * {@link ApplicationModuleInitializer} and {@link ApplicationModule}.
		 *
		 * @param delegate must not be {@literal null}.
		 * @param modules must not be {@literal null}.
		 */
		public LoggingApplicationModuleInitializerAdapter(ApplicationModuleInitializer delegate,
				ApplicationModules modules) {

			Assert.notNull(delegate, "ApplicationModuleInitializer must not be null!");
			Assert.notNull(modules, "ApplicationModules must not be null!");

			this.delegate = delegate;
			this.modules = modules;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.ApplicationModuleInitializer#initialize()
		 */
		@Override
		public void initialize() {

			var listenerType = AopUtils.getTargetClass(delegate);
			var formattable = FormatableType.of(listenerType);

			var formattedListenerType = modules.getModuleByType(listenerType)
					.map(formattable::getAbbreviatedFullName)
					.orElseGet(formattable::getAbbreviatedFullName);

			LOGGER.debug("Initializing {}.", formattedListenerType);

			delegate.initialize();

			LOGGER.debug("Initializing {} done.", formattedListenerType);
		}
	}

	private static class ApplicationModulesBootstrap {

		private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModulesBootstrap.class);
		private static final ApplicationModulesFactory BOOTSTRAP;

		static {

			var factories = SpringFactoriesLoader.loadFactories(ApplicationModulesFactory.class,
					ApplicationModulesBootstrap.class.getClassLoader());

			BOOTSTRAP = !factories.isEmpty() ? factories.get(0) : ApplicationModulesFactory.defaultFactory();
		}

		static ApplicationModules initializeApplicationModules(Class<?> applicationMainClass) {

			LOGGER.debug("Obtaining Spring Modulith application modules…");

			var result = BOOTSTRAP.of(applicationMainClass);
			var numberOfModules = result.stream().count();

			if (numberOfModules == 0) {

				LOGGER.warn("No application modules detected!");

			} else {

				LOGGER.debug("Detected {} application modules: {}", //
						numberOfModules, //
						result.stream().map(ApplicationModule::getName).toList());
			}

			return result;
		}
	}

	/**
	 * Auto-configuration to react to ArchUnit missing on the runtime classpath.
	 *
	 * @author Michael Weirauch
	 * @author Oliver Drotbohm
	 */
	@AutoConfiguration
	@ConditionalOnMissingClass("com.tngtech.archunit.core.importer.ClassFileImporter")
	static class ArchUnitRuntimeDependencyMissingConfiguration {

		private static final String DESCRIPTION = "The Spring Modulith runtime support requires ArchUnit to be on the runtime classpath. This might be caused by it declared as test scope dependency, as it usually is used in tests only.";
		private static final String SUGGESTED_ACTION = "Add ArchUnit to your project and ensure it configured to live in the runtime classpath at least.";

		ArchUnitRuntimeDependencyMissingConfiguration() {
			throw new MissingRuntimeDependency(DESCRIPTION, SUGGESTED_ACTION);
		}
	}
}
