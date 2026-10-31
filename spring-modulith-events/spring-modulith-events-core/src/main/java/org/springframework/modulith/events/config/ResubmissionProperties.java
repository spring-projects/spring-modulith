/*
 * Copyright 2026 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.modulith.events.EventPublication;

/**
 * Configuration properties to tweak event publication resubmission, including the framework-provided default
 * {@link org.springframework.modulith.events.AbandonPolicy}, applied via
 * {@link org.springframework.modulith.events.core.AbandonPolicies}; see
 * {@link EventPublicationAutoConfiguration#abandonPolicies(org.springframework.beans.factory.ObjectProvider, ResubmissionProperties)}.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
@ConfigurationProperties("spring.modulith.events.resubmission")
public class ResubmissionProperties {

	/**
	 * The number of resubmission attempts after which an {@link EventPublication} is considered abandoned, i.e. will
	 * not be resubmitted again. A value of {@literal -1} (the default) disables abandoning altogether.
	 */
	private final int abandonAfterAttempts;

	@ConstructorBinding
	ResubmissionProperties(@Nullable Integer abandonAfterAttempts) {
		this.abandonAfterAttempts = abandonAfterAttempts == null ? -1 : abandonAfterAttempts;
	}

	/**
	 * Returns whether the given {@link EventPublication} should be abandoned according to the configured
	 * {@link #abandonAfterAttempts} threshold.
	 *
	 * @param publication must not be {@literal null}.
	 */
	boolean shouldAbandon(EventPublication publication) {

		return abandonAfterAttempts != -1
				&& publication.getCompletionAttempts() >= abandonAfterAttempts;
	}
}
