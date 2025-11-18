/*
 * Copyright 2023-2025 the original author or authors.
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

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.modulith.core.util.ApplicationModulesExporter;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

import com.jayway.jsonpath.internal.function.PathFunctionFactory;

/**
 * Renders the application module description JSON into a resource named
 * {@value ApplicationModulesExporter#DEFAULT_LOCATION}.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
class ApplicationModulesFileGeneratingProcessor implements BeanFactoryInitializationAotProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModulesFileGeneratingProcessor.class);

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor#processAheadOfTime(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
	 */
	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {

		return (context, __) -> {

			var runtime = beanFactory.getBean(ApplicationModulesRuntime.class);
			var exporter = new ApplicationModulesExporter(runtime.get());
			var location = ApplicationModulesExporter.DEFAULT_LOCATION;

			LOGGER.info("Generating application modules information to {}", location);

			context.getRuntimeHints().resources().registerPattern(location);

			context.getGeneratedFiles().handleFile(Kind.RESOURCE, location, it -> {

				var resource = new ByteArrayResource(exporter.toJson().getBytes(StandardCharsets.UTF_8));

				if (it.exists()) {
					it.override(resource);
				} else {
					it.create(resource);
				}
			});

			// Register JSONPath internals as available for reflective construction to be able to read the generated files in
			// a native image
			//
			// TODO: Remove once https://github.com/json-path/JsonPath/issues/1042 is fixed

			var reflection = context.getRuntimeHints().reflection();
			var classLoader = ApplicationModulesFileGeneratingProcessor.class.getClassLoader();

			PathFunctionFactory.FUNCTIONS.values().forEach(it -> {

				// Tweak name due to shading
				var typeName = it.getName().replace("com.jayway.jsonpath", "org.springframework.modulith.runtime.jsonpath");

				reflection.registerTypeIfPresent(classLoader, typeName, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
			});
		};
	}
}
