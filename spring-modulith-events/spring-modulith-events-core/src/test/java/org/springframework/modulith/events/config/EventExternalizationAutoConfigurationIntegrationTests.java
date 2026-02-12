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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Integration tests for {@link EventExternalizationAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
class EventExternalizationAutoConfigurationIntegrationTests {

	@Test // GH-1370
	void doesNotExternalizationSerializationByDefault() {
		assertSerializationEnabled(null, false);
	}

	@Test // GH-1370
	void configurationPropertyEnablesOrDisablesSerialization() {

		assertSerializationEnabled(true, true);
		assertSerializationEnabled(false, false);
	}

	private static void assertSerializationEnabled(Boolean propertyValue, boolean expected) {

		var runner = new ApplicationContextRunner()
				.withUserConfiguration(SampleApplication.class)
				.withConfiguration(AutoConfigurations.of(EventExternalizationAutoConfiguration.class));

		if (propertyValue != null) {

			runner = runner
					.withPropertyValues("spring.modulith.events.externalization.serialize-externalization=" + propertyValue);
		}

		runner.run(ctx -> {

			assertThat(ctx).getBean(EventExternalizationConfiguration.class).satisfies(it -> {
				assertThat(it.serializeExternalization()).isEqualTo(expected);
			});
		});
	}

	@AutoConfigurationPackage
	static class SampleApplication {}
}
