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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactoryInitializer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ApplicationModulesFactory;
import org.springframework.modulith.core.VerificationOptions;
import org.springframework.modulith.core.util.ApplicationModulesExporter;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;

/**
 * Auto-configuration to register an {@link ApplicationRuntime}, a {@link ApplicationModulesRuntime} and an
 * {@link ApplicationListener} to invoke all {@link ApplicationModuleInitializer}s as Spring Bean.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
@EnableConfigurationProperties(SpringModulithRuntimeProperties.class)
class SpringModulithRuntimeAutoConfiguration {

	private static final Logger LOG = LoggerFactory.getLogger(SpringModulithRuntimeAutoConfiguration.class);

	@Bean
	@Lazy
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean(ApplicationRuntime.class)
	static ApplicationRuntime modulithsApplicationRuntime(ApplicationContext context) {
		return ApplicationRuntime.of(context);
	}

	@Bean
	@Lazy
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean
	static ApplicationModulesRuntime modulesRuntime(ApplicationModulesBootstrap bootstrap, ApplicationRuntime runtime) {
		return new ApplicationModulesRuntime(() -> bootstrap.getApplicationModules().join(), runtime);
	}

	@Bean
	@Lazy
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean
	static ApplicationModulesBootstrap applicationModulesInitializer(ApplicationRuntime runtime,
			ConfigurableBeanFactory factory) {
		return new ApplicationModulesBootstrap(runtime.getMainApplicationClass(), factory.getBootstrapExecutor());
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnBean(ApplicationModuleInitializer.class)
	static ApplicationListener<ApplicationStartedEvent> applicationModuleInitializingListener(
			ApplicationModuleInitializerInvoker invoker, ObjectProvider<ApplicationModuleInitializer> initializers) {
		return __ -> invoker.invokeInitializers(initializers.stream());
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnBooleanProperty(value = "spring.modulith.runtime.verification-enabled", matchIfMissing = false)
	static RuntimeApplicationModuleVerifier applicationModuleVerifier(ApplicationModulesBootstrap bootstrap,
			ObjectProvider<VerificationOptions> verification) {

		return new RuntimeApplicationModuleVerifier(bootstrap.getApplicationModules(), verification);
	}

	/**
	 * {@link ApplicationModuleMetadata} obtained from the Spring Modulith metadata located at
	 * {@value ApplicationModulesExporter#DEFAULT_LOCATION}.
	 *
	 * @param metadata will never be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static ApplicationModuleMetadata applicationModuleMetadata(
			@Value("classpath:" + ApplicationModulesExporter.DEFAULT_LOCATION) Resource metadata) {
		return ApplicationModuleMetadata.of(metadata);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnBean(ApplicationModuleInitializer.class)
	static ApplicationModuleInitializerInvoker applicationModuleInitializerInvoker(ApplicationModuleMetadata metadata,
			ObjectProvider<ApplicationModulesRuntime> runtime) {

		return metadata.isPresent()
				? new PrecomputedApplicationModuleInitializerInvoker(metadata)
				: new DefaultApplicationModuleInitializerInvoker(runtime.getObject().get());
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	ApplicationModuleIdentifiers applicationModuleIdentifiers(ApplicationModuleMetadata metadata,
			ObjectProvider<ApplicationModulesRuntime> runtime) {

		return metadata.isPresent()
				? ApplicationModuleIdentifiers.of(metadata.getIdentifiers())
				: ApplicationModuleIdentifiers.of(runtime.getObject().get());
	}

	static class ApplicationModulesBootstrap {

		private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModulesBootstrap.class);
		private static final ApplicationModulesFactory BOOTSTRAP;

		private final CompletableFuture<ApplicationModules> modules;

		static {

			var factories = SpringFactoriesLoader.loadFactories(ApplicationModulesFactory.class,
					ApplicationModulesBootstrap.class.getClassLoader());

			BOOTSTRAP = !factories.isEmpty() ? factories.get(0) : ApplicationModulesFactory.defaultFactory();
		}

		ApplicationModulesBootstrap(Class<?> applicationMainClass, @Nullable Executor executor) {

			Supplier<ApplicationModules> supplier = () -> initializeApplicationModules(applicationMainClass);

			this.modules = executor == null
					? CompletableFuture.supplyAsync(supplier)
					: CompletableFuture.supplyAsync(supplier, executor);
		}

		CompletableFuture<ApplicationModules> getApplicationModules() {
			return modules;
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
						result.stream().map(ApplicationModule::getIdentifier).toList());
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

	/**
	 * A component to verify the application module arrangement at runtime.
	 *
	 * @author Oliver Drotbohm
	 * @since 2.0
	 */
	static class RuntimeApplicationModuleVerifier
			implements BeanFactoryInitializer<ListableBeanFactory>, SmartInitializingSingleton {

		private final CompletableFuture<Void> modules;

		RuntimeApplicationModuleVerifier(CompletableFuture<ApplicationModules> modules,
				ObjectProvider<VerificationOptions> verification) {

			this.modules = modules.thenAccept(it -> {
				it.verify(verification.getIfAvailable(VerificationOptions::defaults));
				LOG.info("Spring Modulith application module verification completed successfully.");
			});
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.SmartInitializingSingleton#afterSingletonsInstantiated()
		 */
		@Override
		public void afterSingletonsInstantiated() {
			modules.join();
		}

		public void initialize(ListableBeanFactory beanFactory) {};
	}
}
