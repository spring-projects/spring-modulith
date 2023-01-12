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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.FormatableType;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.util.Assert;

/**
 * Auto-configuration to register a {@link SpringBootApplicationRuntime} and {@link ApplicationModulesRuntime} as Spring
 * Bean.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
class SpringModulithRuntimeAutoConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpringModulithRuntimeAutoConfiguration.class);

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean(ApplicationRuntime.class)
	SpringBootApplicationRuntime modulithsApplicationRuntime(ApplicationContext context) {
		return new SpringBootApplicationRuntime(context);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean
	ApplicationModulesRuntime modulesRuntime(ApplicationRuntime runtime) {

		var mainClass = runtime.getMainApplicationClass();
		var modules = Executors.newFixedThreadPool(1)
				.submit(() -> ApplicationModulesBootstrap.initializeApplicationModules(mainClass));

		return new ApplicationModulesRuntime(toSupplier(modules), runtime);
	}

	@Bean
	ApplicationListener<ApplicationStartedEvent> applicationModuleInitialzingListener(ApplicationModulesRuntime runtime,
			List<ApplicationModuleInitializer> initializers) {

		return event -> {

			var modules = runtime.get();

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

		static ApplicationModules initializeApplicationModules(Class<?> applicationMainClass) {

			LOGGER.debug("Obtaining Spring Modulith application modules…");

			var result = ApplicationModules.of(applicationMainClass);
			var numberOfModules = result.stream().count();

			if (numberOfModules == 0) {

				LOGGER.warn("No application modules detected!");

			} else {

				LOGGER.debug("Detected {} application modules: {}", //
						result.stream().count(), //
						result.stream().map(ApplicationModule::getName).toList());
			}

			return result;
		}
	}

	private static Supplier<ApplicationModules> toSupplier(Future<ApplicationModules> modules) {

		return () -> {
			try {
				return modules.get();
			} catch (Exception o_O) {
				throw new RuntimeException(o_O);
			}
		};
	}
}
