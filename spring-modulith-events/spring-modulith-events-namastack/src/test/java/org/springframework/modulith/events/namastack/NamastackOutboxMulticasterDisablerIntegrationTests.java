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
package org.springframework.modulith.events.namastack;

import static org.assertj.core.api.Assertions.*;

import io.namastack.outbox.Outbox;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.PropertySource;
import org.springframework.modulith.events.core.EventPublicationRegistry;

/**
 * Integration tests for {@link OutboxMulticasterDisabler}.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
class NamastackOutboxMulticasterDisablerIntegrationTests {

	@Test // GH-1517
	void registersDefaultingPropertySourceIfOutboxIsPresent() {

		var context = createApplicationContext(null);
		var source = context.getEnvironment().getPropertySources();

		assertThat(source)
				.extracting(PropertySource::getName)
				.element(source.size() - 2) // last custom property source
				.isEqualTo("Outbox defaults");
	}

	@Test // GH-1517
	void doesNotRegisterDefaultingPropertySourceIfOutboxIsNotPresent() {

		var context = createApplicationContext(new FilteredClassLoader(Outbox.class));
		var source = context.getEnvironment().getPropertySources();

		assertThat(source)
				.extracting(PropertySource::getName)
				.doesNotContain("Outbox defaults");
	}

	private static ConfigurableApplicationContext createApplicationContext(ClassLoader classLoader) {

		var application = new SpringApplication(Object.class) {

			/*
			 * (non-Javadoc)
			 * @see org.springframework.boot.SpringApplication#getClassLoader()
			 */
			@Override
			public ClassLoader getClassLoader() {
				return classLoader == null ? super.getClassLoader() : classLoader;
			}
		};

		return application.run(new String[0]);
	}

	@SpringBootApplication
	static class SampleApplication {

		@Bean
		EventPublicationRegistry eventPublicationRegistry() {
			return Mockito.mock(EventPublicationRegistry.class);
		}
	}
}
