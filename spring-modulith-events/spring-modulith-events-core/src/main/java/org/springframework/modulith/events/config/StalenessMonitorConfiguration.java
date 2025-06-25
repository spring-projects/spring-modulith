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
package org.springframework.modulith.events.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.modulith.events.core.EventPublicationProperties;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.support.EventUtils;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.Assert;

/**
 * Configures a fixed-delay task to mark stale in-flight event publications as failed according to the configuration in
 * {@link EventPublicationProperties}.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
@AutoConfiguration
class StalenessMonitorConfiguration implements SchedulingConfigurer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StalenessMonitorConfiguration.class);

	private final EventPublicationRegistry registry;
	private final EventPublicationProperties properties;

	/**
	 * Creates a new {@link StalenessMonitorConfiguration} for the given {@link EventPublicationRegistry} and
	 * {@link EventPublicationProperties}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	StalenessMonitorConfiguration(EventPublicationRegistry registry, EventPublicationProperties properties) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");
		Assert.notNull(properties, "EventPublicationRepositoryProperties must not be null!");

		this.registry = registry;
		this.properties = properties;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.scheduling.annotation.SchedulingConfigurer#configureTasks(org.springframework.scheduling.config.ScheduledTaskRegistrar)
	 */
	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {

		if (properties.monitorStaleness()) {

			LOGGER.info("Checking for stale event publications every {}.",
					EventUtils.prettyPrint(properties.getStalenessCheckIntervall()));

			taskRegistrar.addFixedDelayTask(registry::markStalePublicationsFailed,
					properties.getStalenessCheckIntervall());
		}
	}
}
