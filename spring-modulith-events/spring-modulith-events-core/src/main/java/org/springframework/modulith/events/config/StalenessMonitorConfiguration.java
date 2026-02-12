/*
 * Copyright 2025-2026 the original author or authors.
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
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.Assert;

/**
 * Configures a fixed-delay task to mark stale in-flight event publications as failed according to the configuration in
 * {@link StalenessProperties}.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
@AutoConfiguration
class StalenessMonitorConfiguration implements SchedulingConfigurer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StalenessMonitorConfiguration.class);

	private final EventPublicationRegistry registry;
	private final StalenessProperties staleness;

	/**
	 * Creates a new {@link StalenessMonitorConfiguration} for the given {@link EventPublicationRegistry} and
	 * {@link StalenessProperties}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param staleness must not be {@literal null}.
	 */
	StalenessMonitorConfiguration(EventPublicationRegistry registry, StalenessProperties staleness) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");
		Assert.notNull(staleness, "EventPublicationStaleness must not be null!");

		this.registry = registry;
		this.staleness = staleness;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.scheduling.annotation.SchedulingConfigurer#configureTasks(org.springframework.scheduling.config.ScheduledTaskRegistrar)
	 */
	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {

		if (staleness.monitorStaleness()) {

			LOGGER.info("Checking for stale event publications every {}.",
					EventUtils.prettyPrint(staleness.getCheckIntervall()));

			taskRegistrar.addFixedDelayTask(() -> registry.markStalePublicationsFailed(staleness),
					staleness.getCheckIntervall());
		}
	}
}
