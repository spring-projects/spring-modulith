/*
 * Copyright 2018-2025 the original author or authors.
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.JavaPackage;
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
	public ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes) {

		var moduleTest = TestContextAnnotationUtils.findAnnotationDescriptor(testClass, ApplicationModuleTest.class);

		return moduleTest == null ? null : new ModuleContextCustomizer(moduleTest.getRootDeclaringClass());
	}

	static class ModuleContextCustomizer implements ContextCustomizer {

		private static final Logger LOGGER = LoggerFactory.getLogger(ModuleContextCustomizer.class);

		private final Supplier<ModuleTestExecution> execution;
		private final Class<?> source;

		ModuleContextCustomizer(Class<?> testClass) {

			this.execution = ModuleTestExecution.of(testClass);
			this.source = testClass;
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

			var events = new DefaultPublishedEvents();
			beanFactory.registerSingleton(events.getClass().getName(), events);
			context.addApplicationListener(events);
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

				LOGGER.info("> "
						+ extraIncludes.stream().map(ApplicationModule::getName).collect(Collectors.joining(", ")));
			}

			var sharedModules = modules.getSharedModules();

			if (!sharedModules.isEmpty()) {

				logHeadline("Shared modules:");

				LOGGER.info("> "
						+ sharedModules.stream().map(ApplicationModule::getName).collect(Collectors.joining(", ")));
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

			return Objects.equals(this.source, that.source);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(source);
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

				var type = factory.getType(name, false);
				var module = modules.getModuleByType(type)
						.filter(Predicate.not(ApplicationModule::isRootModule));

				// Not a module type -> pass
				if (module.isEmpty()) {
					continue;
				}

				var packagesIncludedInTestRun = execution.getBasePackages().toList();

				// A type of a module bootstrapped -> pass
				if (module.map(ApplicationModule::getBasePackage)
						.map(JavaPackage::getName)
						.filter(packagesIncludedInTestRun::contains).isPresent()) {
					continue;
				}

				LOGGER.trace(
						"Dropping bean definition {} for type {} as it is not included in an application module to be bootstrapped!",
						name, type.getName());

				// Remove bean definition from bootstrap
				registry.removeBeanDefinition(name);
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor#postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
		 */
		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}
	}
}
