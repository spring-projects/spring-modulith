/*
 * Copyright 2018-2023 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

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

		ApplicationModuleTest moduleTest = AnnotatedElementUtils.getMergedAnnotation(testClass,
				ApplicationModuleTest.class);

		return moduleTest == null ? null : new ModuleContextCustomizer(testClass);
	}

	@Slf4j
	@EqualsAndHashCode
	static class ModuleContextCustomizer implements ContextCustomizer {

		private static final String BEAN_NAME = ModuleTestExecution.class.getName();

		private final Supplier<ModuleTestExecution> execution;

		private ModuleContextCustomizer(Class<?> testClass) {
			this.execution = ModuleTestExecution.of(testClass);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.test.context.ContextCustomizer#customizeContext(org.springframework.context.ConfigurableApplicationContext, org.springframework.test.context.MergedContextConfiguration)
		 */
		@Override
		public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {

			ModuleTestExecution testExecution = execution.get();

			logModules(testExecution);

			ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
			beanFactory.registerSingleton(BEAN_NAME, testExecution);

			DefaultPublishedEvents events = new DefaultPublishedEvents();
			beanFactory.registerSingleton(events.getClass().getName(), events);
			context.addApplicationListener(events);
		}

		private static void logModules(ModuleTestExecution execution) {

			var module = execution.getModule();
			var modules = execution.getModules();
			var moduleName = module.getDisplayName();
			var bootstrapMode = execution.getBootstrapMode().name();

			var message = "Bootstrapping @%s for %s in mode %s (%s)…"
					.formatted(ApplicationModuleTest.class.getName(), moduleName, bootstrapMode, modules.getModulithSource());

			LOG.info(message);
			LOG.info("");

			Arrays.stream(module.toString(modules).split("\n")).forEach(LOG::info);

			List<ApplicationModule> extraIncludes = execution.getExtraIncludes();

			if (!extraIncludes.isEmpty()) {

				logHeadline("Extra includes:");

				LOG.info("> " + extraIncludes.stream().map(ApplicationModule::getName).collect(Collectors.joining(", ")));
			}

			Set<ApplicationModule> sharedModules = modules.getSharedModules();

			if (!sharedModules.isEmpty()) {

				logHeadline("Shared modules:");

				LOG.info("> " + sharedModules.stream().map(ApplicationModule::getName).collect(Collectors.joining(", ")));
			}

			List<ApplicationModule> dependencies = execution.getDependencies();

			if (!dependencies.isEmpty() || !sharedModules.isEmpty()) {

				logHeadline("Included dependencies:");

				Stream<ApplicationModule> dependenciesPlusMissingSharedOnes = //
						Stream.concat(dependencies.stream(), sharedModules.stream() //
								.filter(it -> !dependencies.contains(it)));

				dependenciesPlusMissingSharedOnes //
						.map(it -> it.toString(modules)) //
						.forEach(it -> {
							LOG.info("");
							Arrays.stream(it.split("\n")).forEach(LOG::info);
						});
			}

			LOG.info("");
		}

		private static void logHeadline(String headline) {
			logHeadline(headline, () -> {});
		}

		private static void logHeadline(String headline, Runnable additional) {

			LOG.info("");
			LOG.info(headline);
			additional.run();
		}
	}
}
