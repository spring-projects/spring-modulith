/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic utility for looking up strategy implementations based on configuration. Supports property-based
 * configuration, predefined strategies, custom class instantiation, and {@link SpringFactoriesLoader} fallback.
 *
 * @param <T> the strategy type
 * @since 1.4
 */
public class StrategyLookup<T> {

	private static final Logger LOG = LoggerFactory.getLogger(StrategyLookup.class);

	private final String propertyName;
	private final Class<T> strategyType;
	private final Map<String, Supplier<T>> predefinedStrategies;
	private final Supplier<T> fallbackSupplier;

	/**
	 * Creates a new {@link StrategyLookup} instance.
	 *
	 * @param propertyName         the configuration property name (e.g., "spring.modulith.detection-strategy")
	 * @param strategyType         the strategy class
	 * @param predefinedStrategies map of predefined strategy names to their suppliers
	 * @param fallbackSupplier     the fallback strategy supplier
	 */
	public StrategyLookup(String propertyName, Class<T> strategyType, Map<String, Supplier<T>> predefinedStrategies,
						  Supplier<T> fallbackSupplier) {

		this.propertyName = propertyName;
		this.strategyType = strategyType;
		this.predefinedStrategies = predefinedStrategies;
		this.fallbackSupplier = fallbackSupplier;
	}

	/**
	 * Looks up and returns the strategy implementation using the following algorithm:
	 * <ol>
	 * <li>Use the predefined strategies if the configured property value matches one of them.</li>
	 * <li>Interpret the configured value as a class name if it doesn't match the predefined values.</li>
	 * <li>Use the {@link SpringFactoriesLoader} if no property is configured (deprecated).</li>
	 * <li>A final fallback on the provided fallback supplier.</li>
	 * </ol>
	 *
	 * @return the strategy implementation, never {@literal null}
	 */
	public T lookup() {

		var environment = new StandardEnvironment();
		ConfigDataEnvironmentPostProcessor.applyTo(environment,
				new DefaultResourceLoader(StrategyLookup.class.getClassLoader()), null);

		var configuredStrategy = environment.getProperty(propertyName, String.class);

		// Nothing configured? Use SpringFactoriesLoader or fallback
		if (!StringUtils.hasText(configuredStrategy)) {
			return lookupViaSpringFactoriesOrFallback();
		}

		// Check predefined strategies
		var predefined = predefinedStrategies.get(configuredStrategy);

		if (predefined != null) {
			return predefined.get();
		}

		// Try to load configured value as class
		try {

			var strategyClass = ClassUtils.forName(configuredStrategy, strategyType.getClassLoader());
			return BeanUtils.instantiateClass(strategyClass, strategyType);

		} catch (ClassNotFoundException | LinkageError o_O) {
			throw new IllegalStateException("Unable to load strategy class: " + configuredStrategy, o_O);
		}
	}

	/**
	 * Attempts to load strategy via {@link SpringFactoriesLoader} (deprecated), falling back to the fallback supplier
	 * if none found.
	 *
	 * @return the strategy implementation, never {@literal null}
	 */
	private T lookupViaSpringFactoriesOrFallback() {

		List<T> loadFactories = SpringFactoriesLoader.loadFactories(strategyType, strategyType.getClassLoader());

		var size = loadFactories.size();

		if (size == 0) {
			return fallbackSupplier.get();
		}

		if (size > 1) {
			throw new IllegalStateException(
					"Multiple strategies configured via spring.factories. Only one supported! %s".formatted(loadFactories));
		}

		LOG.warn(
				"Configuring strategy via spring.factories is deprecated! Please configure {} instead.",
				propertyName);

		return loadFactories.get(0);
	}
}
