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
package org.springframework.modulith.events.core;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * @author Oliver Drotbohm
 */
@ConfigurationProperties("spring.modulith.events")
public class EventPublicationProperties {

	public static EventPublicationProperties DEFAULTS = new EventPublicationProperties(null, null,
			null);

	private final Duration processingStaleness, resubmissionStaleness, stalenessCheckIntervall;

	/**
	 * @param processingStaleness
	 * @param resubmissionStaleness
	 * @param stalenessCheckIntervall
	 */
	@ConstructorBinding
	public EventPublicationProperties(@Nullable Duration processingStaleness,
			@Nullable Duration resubmissionStaleness,
			@Nullable Duration stalenessCheckIntervall) {
		this.processingStaleness = processingStaleness == null ? Duration.ZERO : processingStaleness;
		this.resubmissionStaleness = resubmissionStaleness == null ? Duration.ZERO : resubmissionStaleness;
		this.stalenessCheckIntervall = stalenessCheckIntervall == null ? Duration.ofMinutes(1) : stalenessCheckIntervall;
	}

	/**
	 * @return the processingStaleness
	 */
	public Duration getProcessingStaleness() {
		return processingStaleness;
	}

	/**
	 * @return the resubmissionStaleness
	 */
	public Duration getResubmissionStaleness() {
		return resubmissionStaleness;
	}

	public boolean monitorStaleness() {
		return !processingStaleness.equals(Duration.ZERO)
				|| !resubmissionStaleness.equals(Duration.ZERO);
	}

	public Duration getStalenessCheckIntervall() {
		return stalenessCheckIntervall;
	}
}
