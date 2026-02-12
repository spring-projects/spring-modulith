/*
 * Copyright 2018-2026 the original author or authors.
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
package org.springframework.modulith.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.util.Assert;

/**
 * @author Oliver Drotbohm
 */
class ModuleContextCustomizerFactory implements ContextCustomizerFactory {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.test.context.ContextCustomizerFactory#createContextCustomizer(java.lang.Class, java.util.List)
	 */
	@Override
	public @Nullable ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes) {

		var moduleTest = TestContextAnnotationUtils.findAnnotationDescriptor(testClass, ApplicationModuleTest.class);

		return moduleTest == null ? null : new ModuleContextCustomizer(moduleTest.getRootDeclaringClass());
	}

	static class ModuleContextCustomizer implements ContextCustomizer {

		private static final Logger LOGGER = LoggerFactory.getLogger(ModuleContextCustomizer.class);

		private final Supplier<ModuleTestExecution> execution;

		ModuleContextCustomizer(Class<?> testClass) {
			this.execution = ModuleTestExecution.of(testClass);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.test.context.ContextCustomizer#customizeContext(org.springframework.context.ConfigurableApplicationContext, org.springframework.test.context.MergedContextConfiguration)
		 */
		@Override
		public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {

			var testExecution = execution.get();

			logModules(testExecution);

			var beanFactory = context.getBeanFactory();
			beanFactory.registerSingleton(ModuleTestExecution.class.getName(), testExecution);
			beanFactory.registerSingleton(ModuleTestExecutionBeanDefinitionSelector.class.getName(),
					new ModuleTestExecutionBeanDefinitionSelector(testExecution));

			var events = PublishedEventsFactory.createPublishedEvents();
			beanFactory.registerSingleton(events.getClass().getName(), events);
			context.addApplicationListener(events);
		}

		/**
		 * Returns the underlying initialized {@link ModuleTestExecution}.
		 *
		 * @return will never be {@literal null}.
		 */
		ModuleTestExecution getExecution() {
			return execution.get();
		}

		private static void logModules(ModuleTestExecution execution) {

			var module = execution.getModule();
			var modules = execution.getModules();
			var moduleName = module.getDisplayName();
			var bootstrapMode = execution.getBootstrapMode().name();

			var message = "Bootstrapping @%s for %s in mode %s (%s)…"
					.formatted(ApplicationModuleTest.class.getName(), moduleName, bootstrapMode, modules.getSource());

			LOGGER.info(message);
			LOGGER.info("");

			Arrays.stream(module.toString(modules).split("\n")).forEach(LOGGER::info);

			var extraIncludes = execution.getExtraIncludes();

			if (!extraIncludes.isEmpty()) {

				logHeadline("Extra includes:");

				LOGGER.info("> " + extraIncludes.stream()
						.map(ApplicationModule::getIdentifier)
						.map(ApplicationModuleIdentifier::toString)
						.collect(Collectors.joining(", ")));
			}

			var sharedModules = modules.getSharedModules();

			if (!sharedModules.isEmpty()) {

				logHeadline("Shared modules:");

				LOGGER.info("> " + sharedModules.stream()
						.map(ApplicationModule::getIdentifier)
						.map(ApplicationModuleIdentifier::toString)
						.collect(Collectors.joining(", ")));
			}

			var dependencies = execution.getDependencies();

			if (!dependencies.isEmpty() || !sharedModules.isEmpty()) {

				logHeadline("Included dependencies:");

				var dependenciesPlusMissingSharedOnes = Stream.concat(dependencies.stream(), sharedModules.stream() //
						.filter(it -> !dependencies.contains(it)));

				dependenciesPlusMissingSharedOnes //
						.map(it -> it.toString(modules)) //
						.forEach(it -> {
							LOGGER.info("");
							Arrays.stream(it.split("\n")).forEach(LOGGER::info);
						});
			}

			LOGGER.info("");
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof ModuleContextCustomizer that)) {
				return false;
			}

			return Objects.equals(this.execution.get(), that.execution.get());
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(execution.get());
		}

		private static void logHeadline(String headline) {
			logHeadline(headline, () -> {});
		}

		private static void logHeadline(String headline, Runnable additional) {

			LOGGER.info("");
			LOGGER.info(headline);
			additional.run();
		}
	}

	/**
	 * A {@link BeanDefinitionRegistryPostProcessor} that selects
	 * {@link org.springframework.beans.factory.config.BeanDefinition}s that are either non-module beans (i.e.
	 * infrastructure) or beans living inside an {@link ApplicationModule} being part of the current
	 * {@link ModuleTestExecution}.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.1
	 */
	private static class ModuleTestExecutionBeanDefinitionSelector implements BeanDefinitionRegistryPostProcessor {

		private static final Logger LOGGER = LoggerFactory.getLogger(ModuleTestExecutionBeanDefinitionSelector.class);

		private final ModuleTestExecution execution;

		/**
		 * Creates a new {@link ModuleTestExecutionBeanDefinitionSelector} for the given {@link ModuleTestExecution}.
		 *
		 * @param execution must not be {@literal null}.
		 */
		private ModuleTestExecutionBeanDefinitionSelector(ModuleTestExecution execution) {

			Assert.notNull(execution, "ModuleTestExecution must not be null!");

			this.execution = execution;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry)
		 */
		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {

			if (!(registry instanceof ConfigurableListableBeanFactory factory)) {
				return;
			}

			var modules = execution.getModules();

			for (String name : registry.getBeanDefinitionNames()) {

				if (include(name, modules, factory)) {
					continue;
				}

				var type = factory.getType(name, false);

				if (type == null) {
					continue;
				}

				LOGGER.trace(
						"Dropping bean definition {} for type {} as it is not included in an application module to be bootstrapped!",
						name, type.getName());

				// Remove bean definition from bootstrap
				registry.removeBeanDefinition(name);
			}
		}

		private boolean include(String beanDefinitionName, ApplicationModules modules,
				ConfigurableListableBeanFactory factory) {

			var types = getTypeOrTestConfigurationFactoryBean(beanDefinitionName, factory);

			var result = modules.stream()
					.filter(Predicate.not(ApplicationModule::isRootModule))
					.filter(it -> types.stream().anyMatch(type -> it.couldContain(type)))
					.map(ApplicationModule::getBasePackage)
					.map(execution.getBasePackages()::contains)
					.toList();

			return result.isEmpty() || result.contains(true);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor#postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
		 */
		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}

		/**
		 * Returns the type of the {@link org.springframework.beans.factory.config.BeanDefinition} of the given name and
		 * optionally its factory bean's type if annotated with {@link TestConfiguration}.
		 *
		 * @param beanDefinitionName must not be {@literal null} or empty.
		 * @param factory must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		private static Collection<Class<?>> getTypeOrTestConfigurationFactoryBean(String beanDefinitionName,
				ConfigurableListableBeanFactory factory) {

			var result = new ArrayList<Class<?>>();
			var type = factory.getType(beanDefinitionName, false);

			if (type != null) {
				result.add(type);
			}

			var factoryName = factory.getBeanDefinition(beanDefinitionName).getFactoryBeanName();

			if (factoryName == null) {
				return result;
			}

			var factoryType = factory.getType(factoryName, false);

			if (factoryType == null) {
				return result;
			}

			if (AnnotatedElementUtils.hasAnnotation(factoryType, TestConfiguration.class)) {
				result.add(factoryType);
			}

			return result;
		}
	}
}
