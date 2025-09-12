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
package org.springframework.modulith.events.kafka;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * @author Oliver Drotbohm
 */
class KafkaJacksonConfigurationIntegrationTests {

	@Test
	void initializesJackson3ByDefault() {

		Stream.of(null, new FilteredClassLoader("com.fasterxml")).forEach(it -> {

			createRunner()
					.withClassLoader(it)
					.run(ctxt -> {
						assertThat(ctxt).hasSingleBean(ByteArrayJacksonJsonMessageConverter.class);
						assertThat(ctxt).doesNotHaveBean(ByteArrayJsonMessageConverter.class);
					});
		});
	}

	@Test
	void initializesJackson2If3IsNotOnTheClasspath() {

		createRunner()
				.withClassLoader(new FilteredClassLoader("tools.jackson"))
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(ByteArrayJsonMessageConverter.class);
					assertThat(ctxt).doesNotHaveBean(ByteArrayJacksonJsonMessageConverter.class);
				});
	}

	/**
	 * @return
	 */
	private static ApplicationContextRunner createRunner() {
		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KafkaJackson2Configuration.class, KafkaJacksonConfiguration.class));
	}
}
