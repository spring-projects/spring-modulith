/*
 * Copyright 2025 the original author or authors.
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

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.FormattableType;
import org.springframework.util.Assert;

class LoggingApplicationModuleInitializerAdapter implements ApplicationModuleInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingApplicationModuleInitializerAdapter.class);

	private final ApplicationModuleInitializer delegate;
	private final String label;

	/**
	 * Creates a new {@link LoggingApplicationModuleInitializerAdapter} for the given {@link ApplicationModuleInitializer}
	 * and identifier.
	 *
	 * @param delegate must not be {@literal null}.
	 * @param label must not be {@literal null}.
	 */
	private LoggingApplicationModuleInitializerAdapter(ApplicationModuleInitializer delegate, String label) {

		Assert.notNull(delegate, "ApplicationModuleInitializer must not be null!");
		Assert.hasText(label, "Label must not be null or empty!");

		this.delegate = delegate;
		this.label = label;
	}

	public static ApplicationModuleInitializer of(ApplicationModuleInitializer initializer,
			ApplicationModules modules) {

		if (!LoggerFactory.getLogger(initializer.getClass()).isDebugEnabled()) {
			return initializer;
		}

		var listenerType = AopUtils.getTargetClass(initializer);
		var formattable = FormattableType.of(listenerType);

		var formattedListenerType = modules.getModuleByType(listenerType)
				.map(formattable::getAbbreviatedFullName)
				.orElseGet(formattable::getAbbreviatedFullName);

		return new LoggingApplicationModuleInitializerAdapter(initializer, formattedListenerType);
	}

	public static ApplicationModuleInitializer of(ApplicationModuleInitializer initializer) {

		if (!LoggerFactory.getLogger(initializer.getClass()).isDebugEnabled()) {
			return initializer;
		}

		var listenerType = AopUtils.getTargetClass(initializer);
		var abbreviatedPackage = Stream.of(listenerType.getPackageName().split("\\."))
				.map(it -> it.substring(0, 1))
				.collect(Collectors.joining("."));

		return new LoggingApplicationModuleInitializerAdapter(initializer,
				abbreviatedPackage + "." + listenerType.getSimpleName());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.ApplicationModuleInitializer#initialize()
	 */
	@Override
	public void initialize() {

		LOGGER.debug("Initializing {}.", label);

		delegate.initialize();

		LOGGER.debug("Initializing {} done.", label);
	}
}
