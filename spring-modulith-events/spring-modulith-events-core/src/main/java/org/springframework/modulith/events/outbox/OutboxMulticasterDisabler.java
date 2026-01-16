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
package org.springframework.modulith.events.outbox;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

/**
 * An {@link EnvironmentPostProcessor} that disables the Namastack multicaster if present on the classpath to make sure
 * it doesn't interfere with the Spring Modulith one.
 *
 * @author Oliver Drotbohm
 * @soundtrack Ed Sheeran - Drive (https://www.youtube.com/watch?v=yfANlm8mhwM)
 * @since 2.1
 */
class OutboxMulticasterDisabler implements EnvironmentPostProcessor {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.boot.EnvironmentPostProcessor#postProcessEnvironment(org.springframework.core.env.ConfigurableEnvironment, org.springframework.boot.SpringApplication)
	 */
	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

		if (ClassUtils.isPresent("io.namastack.outbox.Outbox", application.getClassLoader())) {

			environment.getPropertySources()
					.addLast(new MapPropertySource("Outbox defaults", Map.of("outbox.multicaster.enabled", false)));
		}
	}
}
