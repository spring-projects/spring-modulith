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

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.Staleness;
import org.springframework.util.Assert;

/**
 * Configuration properties to tweak the Spring Modulith event handling.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
@ConfigurationProperties("spring.modulith.events.staleness")
public class StalenessProperties implements Staleness {

	public static StalenessProperties DEFAULTS = new StalenessProperties(null, null, null,
			null);

	/**
	 * Configures after which {@link Duration} an {@link org.springframework.modulith.events.EventPublication} marked as
	 * {@value org.springframework.modulith.events.EventPublication.Status#PROCESSING} will be considered stale.
	 */
	private final Duration published;

	/**
	 * Configures after which {@link Duration} an {@link org.springframework.modulith.events.EventPublication} marked as
	 * {@value org.springframework.modulith.events.EventPublication.Status#PROCESSING} will be considered stale.
	 */
	private final Duration processing;

	/**
	 * Configures after which {@link Duration} an {@link org.springframework.modulith.events.EventPublication} marked as
	 * {@value org.springframework.modulith.events.EventPublication.Status#RESUBMITTED} will be considered stale.
	 */
	private final Duration resubmission;

	/**
	 * Configures the {@link Duration} to check for stale event publications. Defaults to one minute.
	 */
	private final Duration checkIntervall;

	@ConstructorBinding
	StalenessProperties(
			@Nullable Duration published,
			@Nullable Duration processing,
			@Nullable Duration resubmission,
			@Nullable Duration checkIntervall) {

		this.published = published == null ? Duration.ZERO : published;
		this.processing = processing == null ? Duration.ZERO : processing;
		this.resubmission = resubmission == null ? Duration.ZERO : resubmission;
		this.checkIntervall = checkIntervall == null ? Duration.ofMinutes(1) : checkIntervall;
	}

	boolean monitorStaleness() {

		return !published.equals(Duration.ZERO)
				|| !processing.equals(Duration.ZERO)
				|| !resubmission.equals(Duration.ZERO);
	}

	Duration getCheckIntervall() {
		return checkIntervall;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationStaleness#getPublished()
	 */
	@Override
	public Duration getStaleness(Status status) {

		Assert.notNull(status, "Status must not be null!");

		return switch (status) {
			case PUBLISHED -> published;
			case PROCESSING -> processing;
			case RESUBMITTED -> resubmission;
			default -> Duration.ZERO;
		};
	}
}
