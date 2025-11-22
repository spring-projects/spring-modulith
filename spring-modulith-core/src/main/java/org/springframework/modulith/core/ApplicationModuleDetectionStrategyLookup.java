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
package org.springframework.modulith.core;

import org.springframework.modulith.core.config.StrategyLookup;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A factory for the {@link ApplicationModuleDetectionStrategy} to be used when scanning code for
 * {@link ApplicationModule}s.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModuleDetectionStrategyLookup {

	private static final String DETECTION_STRATEGY_PROPERTY = "spring.modulith.detection-strategy";

	/**
	 * Returns the {@link ApplicationModuleDetectionStrategy} to be used to detect {@link ApplicationModule}s. Will use
	 * the following algorithm:
	 * <ol>
	 * <li>Use the prepared strategies if either {@code direct-sub-packages} or {@code explicitly-annotated} is configured
	 * for the {@code spring.modulith.detection-strategy} configuration property.</li>
	 * <li>Interpret the configured value as class if it doesn't match the predefined values just described.</li>
	 * <li>Use the {@link ApplicationModuleDetectionStrategy} declared in {@code META-INF/spring.factories}
	 * (deprecated)</li>
	 * <li>A final fallback on the {@code direct-sub-packages}.</li>
	 * </ol>
	 *
	 * @return will never be {@literal null}.
	 */
	static ApplicationModuleDetectionStrategy getStrategy() {

		Map<String, Supplier<ApplicationModuleDetectionStrategy>> predefinedStrategies = Map.of(
				"direct-sub-packages", ApplicationModuleDetectionStrategy::directSubPackage,
				"explicitly-annotated", ApplicationModuleDetectionStrategy::explicitlyAnnotated);

		var lookup = new StrategyLookup<>(
				DETECTION_STRATEGY_PROPERTY,
				ApplicationModuleDetectionStrategy.class,
				predefinedStrategies,
				ApplicationModuleDetectionStrategy::directSubPackage);

		return lookup.lookup();
	}
}
