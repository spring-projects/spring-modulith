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
import static org.mockito.Mockito.*;

import io.namastack.outbox.handler.OutboxHandler;

import java.util.function.BiConsumer;

import org.jmolecules.event.annotation.Externalized;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.MessageChannel;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalized;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.PublishedEventsFactory;

/**
 * Unit tests for {@link SpringMessagingEventExternalizerConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class SpringMessagingEventExternalizerConfigurationTests {

	static final EventExternalizationConfiguration EXTERNALIZATION_ENABLED = EventExternalizationConfiguration
			.defaults("org").build();

	@Test // GH-1637
	void configuresNamastackOutboxHandlerIfPresent() {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(OutboxHandler.class);
				});
	}

	@Test // GH-1637
	void doesNotConfigureNamastackOutboxHandlerIfNotPresent() {

		basicSetup(EXTERNALIZATION_ENABLED, "io.namastack")
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(OutboxHandler.class);
				});
	}

	@Test // GH-1637
	void configuresJobRunrOutboxHandlerIfPresent() {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).hasSingleBean(JobRunrExternalizationTransport.class);
				});
	}

	@Test // GH-1637
	void doesNotConfigureJobRunrOutboxHandlerIfNotPresent() {

		basicSetup(EXTERNALIZATION_ENABLED, "org.jobrunr")
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {
					assertThat(ctxt).doesNotHaveBean(JobRunrExternalizationTransport.class);
				});
	}

	@Test // GH-1642
	void publishesEventExternalizedAfterJobRunrExternalization() {

		assertEventExternalizedPublished(JobRunrExternalizationTransport.class,
				JobRunrExternalizationTransport::externalize);
	}

	@Test // GH-1642
	void publishesEventExternalizedAfterNamastackExternalization() {
		assertEventExternalizedPublished(OutboxHandler.class, (transport, event) -> transport.handle(event, null));
	}

	private <T> void assertEventExternalizedPublished(Class<T> transportType, BiConsumer<T, Object> consumer) {

		basicSetup(EXTERNALIZATION_ENABLED)
				.withBean(PublishedEvents.class, PublishedEventsFactory::createPublishedEvents)
				.withPropertyValues(ExternalizationMode.PROPERTY + "=" + ExternalizationMode.OUTBOX)
				.run(ctxt -> {

					var transport = ctxt.getBean(transportType);
					var event = new SampleEvent();

					consumer.accept(transport, event);

					var events = ctxt.getBean(PublishedEvents.class);

					assertThat(events.ofType(EventExternalized.class)
							.matching(it -> it.getEvent().equals(event))).hasSize(1);
				});
	}

	private ApplicationContextRunner basicSetup(@Nullable EventExternalizationConfiguration configuration,
			String... excluded) {

		var defaulted = configuration == null ? EventExternalizationConfiguration.disabled() : configuration;

		var runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						SpringMessagingEventExternalizerConfiguration.class,
						EventExternalizationAutoConfiguration.class))
				.withBean(EventExternalizationConfiguration.class, () -> defaulted)
				.withBean("sampleChannel", MessageChannel.class, () -> mock(MessageChannel.class));

		if (excluded.length > 0) {
			runner = runner.withClassLoader(new FilteredClassLoader(excluded));
		}

		return runner;
	}

	@Externalized(target = "sampleChannel")
	static class SampleEvent {}
}
