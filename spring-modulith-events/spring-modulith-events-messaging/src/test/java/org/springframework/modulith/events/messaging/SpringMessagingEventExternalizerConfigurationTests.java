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
package org.springframework.modulith.events.messaging;

import static org.assertj.core.api.Assertions.*;

import io.namastack.outbox.handler.OutboxHandler;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;

/**
 * Unit tests for {@link SpringMessagingEventExternalizerConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class SpringMessagingEventExternalizerConfigurationTests {

	static final EventExternalizationConfiguration EXTERNALIZATION_ENABLED = EventExternalizationConfiguration
			.defaults("org").build();

	@Test
	void configuresNamastackOutboxHandlerIfPresent() {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(OutboxHandler.class);
				});
	}

	@Test
	void doesNotConfigureNamastackOutboxHandlerIfNotPresent() {

		basicSetup(EXTERNALIZATION_ENABLED, "io.namastack")
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(OutboxHandler.class);
				});
	}

	@Test
	void configuresJobRunrOutboxHandlerIfPresent() {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(JobRunrExternalizationTransport.class);
				});
	}

	@Test
	void doesNotConfigureJobRunrOutboxHandlerIfNotPresent() {

		basicSetup(EXTERNALIZATION_ENABLED, "org.jobrunr")
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(JobRunrExternalizationTransport.class);
				});
	}

	private ApplicationContextRunner basicSetup(@Nullable EventExternalizationConfiguration configuration,
			String... excluded) {

		var defaulted = configuration == null ? EventExternalizationConfiguration.disabled() : configuration;

		var runner = new ApplicationContextRunner()
				.withConfiguration(
						AutoConfigurations.of(SpringMessagingEventExternalizerConfiguration.class))
				.withBean(EventExternalizationConfiguration.class, () -> defaulted);

		if (excluded.length > 0) {
			runner = runner.withClassLoader(new FilteredClassLoader(excluded));
		}

		return runner;
	}
}
