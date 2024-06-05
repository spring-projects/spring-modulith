/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.modulith.core;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * A factory for the {@link ApplicationModuleDetectionStrategy} to be used when scanning code for
 * {@link ApplicationModule}s.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModuleDetectionStrategyLookup {

	private static final String DETECTION_STRATEGY_PROPERTY = "spring.modulith.detection-strategy";
	private static final Logger LOG = LoggerFactory.getLogger(ApplicationModuleDetectionStrategyLookup.class);
	private static final Supplier<ApplicationModuleDetectionStrategy> FALLBACK_DETECTION_STRATEGY;

	static {

		FALLBACK_DETECTION_STRATEGY = () -> {

			List<ApplicationModuleDetectionStrategy> loadFactories = SpringFactoriesLoader.loadFactories(
					ApplicationModuleDetectionStrategy.class, ApplicationModules.class.getClassLoader());

			var size = loadFactories.size();

			if (size == 0) {
				return ApplicationModuleDetectionStrategy.directSubPackage();
			}

			if (size > 1) {

				throw new IllegalStateException(
						"Multiple module detection strategies configured. Only one supported! %s".formatted(loadFactories));
			}

			LOG.warn(
					"Configuring the application module detection strategy via spring.factories is deprecated! Please configure {} instead.",
					DETECTION_STRATEGY_PROPERTY);

			return loadFactories.get(0);
		};
	}

	/**
	 * Returns the {@link ApplicationModuleDetectionStrategy} to be used to detect {@link ApplicationModule}s. Will use
	 * the following algorithm:
	 * <ol>
	 * <li>Use the prepared strategies if
	 *
	 * @return
	 */
	static ApplicationModuleDetectionStrategy getStrategy() {

		var environment = new StandardEnvironment();
		ConfigDataEnvironmentPostProcessor.applyTo(environment);

		var configuredStrategy = environment.getProperty(DETECTION_STRATEGY_PROPERTY, String.class);

		// Nothing configured? Use fallback.
		if (!StringUtils.hasText(configuredStrategy)) {
			return FALLBACK_DETECTION_STRATEGY.get();
		}

		// Any of the prepared ones?
		switch (configuredStrategy) {
			case "direct-sub-packages":
				return ApplicationModuleDetectionStrategy.directSubPackage();
			case "explicitly-annotated":
				return ApplicationModuleDetectionStrategy.explictlyAnnotated();
		}

		try {

			// Lookup configured value as class
			var strategyType = ClassUtils.forName(configuredStrategy, ApplicationModules.class.getClassLoader());
			return BeanUtils.instantiateClass(strategyType, ApplicationModuleDetectionStrategy.class);

		} catch (ClassNotFoundException | LinkageError o_O) {
			throw new IllegalStateException(o_O);
		}
	}
}
