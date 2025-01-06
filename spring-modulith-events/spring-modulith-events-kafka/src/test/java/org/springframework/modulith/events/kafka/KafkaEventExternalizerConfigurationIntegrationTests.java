/*
 * Copyright 2023-2025 the original author or authors.
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
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

/**
 * Integration tests for {@link KafkaEventExternalizerConfiguration}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class KafkaEventExternalizerConfigurationIntegrationTests {

	private KafkaOperations<?, ?> operations = mock(KafkaOperations.class);

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

	@Test // GH-855
	void addsHeadersIfConfigured() {

		var config = EventExternalizationConfiguration.defaults("org")
				.headers(Sample.class, __ -> Map.of("key", "value"))
				.build();

		assertMessage(config, it -> {
			assertThat(it.getHeaders()).containsKey("key");
		});
	}

	@Test // GH-855
	void sendsMessageAsIsIfMappingTarget() {

		var config = EventExternalizationConfiguration.defaults("org")
				.mapping(Sample.class, it -> MessageBuilder.withPayload(it).setHeader("key", "value").build())
				.build();

		assertMessage(config, it -> {
			assertThat(it.getHeaders()).contains(Map.entry("key", "value"));
		});
	}

	private void assertMessage(EventExternalizationConfiguration configuration, Consumer<Message<?>> assertions) {

		basicSetup(configuration)
				.run(ctxt -> {

					ctxt.getBean(DelegatingEventExternalizer.class).externalize(new Sample());

					var captor = ArgumentCaptor.forClass(Message.class);
					verify(operations).send(captor.capture());

					assertions.accept(captor.getValue());

				});
	}

	private ApplicationContextRunner basicSetup() {
		return basicSetup(null);
	}

	private ApplicationContextRunner basicSetup(@Nullable EventExternalizationConfiguration config) {

		Supplier<EventExternalizationConfiguration> configProvider = () -> config == null
				? EventExternalizationConfiguration.disabled()
				: config;

		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KafkaEventExternalizerConfiguration.class))
				.withBean(EventExternalizationConfiguration.class, configProvider)
				.withBean(KafkaOperations.class, () -> operations);
	}

	@Externalized
	record Sample() {}
}
