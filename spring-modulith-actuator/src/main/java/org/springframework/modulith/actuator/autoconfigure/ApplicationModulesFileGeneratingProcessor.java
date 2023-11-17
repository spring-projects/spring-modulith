/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.modulith.actuator.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.modulith.core.util.ApplicationModulesExporter;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * Renders the application module description JSON into a resource named
 * {@value ApplicationModulesEndpointConfiguration#FILE_LOCATION}.
 *
 * @author Oliver Drotbohm
 * @since 1.0.3
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
			var location = ApplicationModulesEndpointConfiguration.FILE_LOCATION;

			LOGGER.info("Generating application modules information to {}", location);

			context.getRuntimeHints().resources().registerPattern(location);
			context.getGeneratedFiles().addResourceFile(location, exporter.toJson());
		};
	}
}
