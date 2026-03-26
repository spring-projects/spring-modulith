/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.events.jms;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.namastack.outbox.handler.OutboxHandler;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jms.core.JmsOperations;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

/**
 * Integration tests for {@link JmsEventExternalizerConfiguration}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class JmsEventExternalizerConfigurationIntegrationTests {

	static final EventExternalizationConfiguration EXTERNALIZATION_ENABLED = EventExternalizationConfiguration
			.defaults("org").build();

	@Test // GH-342
	void registersExternalizerByDefault() {

		basicSetup()
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(DelegatingEventExternalizer.class);
				});
	}

	@Test // GH-342
	void disablesExternalizationIfConfigured() {

		basicSetup()
				.withPropertyValues("spring.modulith.events.externalization.enabled=false")
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(DelegatingEventExternalizer.class);
				});
	}

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

	private ApplicationContextRunner basicSetup() {

		return basicSetup(null);
	}

	private ApplicationContextRunner basicSetup(@Nullable EventExternalizationConfiguration configuration,
			String... excluded) {

		var defaulted = configuration == null ? EventExternalizationConfiguration.disabled() : configuration;

		var runner = new ApplicationContextRunner()
				.withConfiguration(
						AutoConfigurations.of(JmsEventExternalizerConfiguration.class))
				.withBean(EventExternalizationConfiguration.class, () -> defaulted)
				.withBean(EventSerializer.class, () -> mock(EventSerializer.class))
				.withBean(JmsOperations.class, () -> mock(JmsOperations.class));

		if (excluded.length > 0) {
			runner = runner.withClassLoader(new FilteredClassLoader(excluded));
		}

		return runner;
	}
}
