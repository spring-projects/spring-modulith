/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.model.Module;
import org.springframework.modulith.model.Modules;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * @author Oliver Gierke
 */
class ModuleContextCustomizerFactory implements ContextCustomizerFactory {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.test.context.ContextCustomizerFactory#createContextCustomizer(java.lang.Class, java.util.List)
	 */
	@Override
	public ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes) {

		ModuleTest moduleTest = AnnotatedElementUtils.getMergedAnnotation(testClass, ModuleTest.class);

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

			Module module = execution.getModule();
			Modules modules = execution.getModules();
			String moduleName = module.getDisplayName();
			String bootstrapMode = execution.getBootstrapMode().name();

			String message = String.format("Bootstrapping @ModuleTest for %s in mode %s (%s)…", moduleName, bootstrapMode,
					modules.getModulithSource());

			LOG.info(message);
			LOG.info(getSeparator("=", message));

			Arrays.stream(module.toString(modules).split("\n")).forEach(LOG::info);

			List<Module> extraIncludes = execution.getExtraIncludes();

			if (!extraIncludes.isEmpty()) {

				logHeadline("Extra includes:", message);

				extraIncludes.forEach(it -> LOG.info("> ".concat(it.getName())));
			}

			Set<Module> sharedModules = modules.getSharedModules();

			if (!sharedModules.isEmpty()) {

				logHeadline("Shared modules:", message);

				sharedModules.forEach(it -> LOG.info("> ".concat(it.getName())));
			}

			List<Module> dependencies = execution.getDependencies();

			if (!dependencies.isEmpty() || !sharedModules.isEmpty()) {

				logHeadline("Included dependencies:", message);

				Stream<Module> dependenciesPlusMissingSharedOnes = //
						Stream.concat(dependencies.stream(), sharedModules.stream() //
								.filter(it -> !dependencies.contains(it)));

				dependenciesPlusMissingSharedOnes //
						.map(it -> it.toString(modules)) //
						.forEach(it -> {
							Arrays.stream(it.split("\n")).forEach(LOG::info);
						});

				LOG.info(getSeparator("=", message));
			}
		}

		private static String getSeparator(String character, String reference) {
			return String.join("", Collections.nCopies(reference.length(), character));
		}

		private static void logHeadline(String headline, String reference) {
			logHeadline(headline, reference, () -> {});
		}

		private static void logHeadline(String headline, String reference, Runnable additional) {

			LOG.info(getSeparator("=", reference));
			LOG.info(headline);
			additional.run();
			LOG.info(getSeparator("=", reference));
		}
	}
}
