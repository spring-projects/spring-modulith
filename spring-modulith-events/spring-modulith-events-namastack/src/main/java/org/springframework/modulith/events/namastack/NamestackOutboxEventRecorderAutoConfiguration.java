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

import io.namastack.outbox.Outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;

/**
 * Auto-configuration to set up an {@link OutboxEventRecorder} to externalize events via the outbox.
 * <p>
 * This configuration is activated when {@code spring.modulith.events.externalization.mode} is set to {@code outbox}.
 * Events are persisted to an outbox table within the same transaction as the business operation.
 *
 * @author Roland Beisel
 * @since 2.1
 * @see ExternalizationMode#OUTBOX
 */
@AutoConfiguration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "outbox")
@ConditionalOnBean(Outbox.class)
class NamastackOutboxEventRecorderAutoConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(NamastackOutboxEventRecorderAutoConfiguration.class);

	@Bean
	NamastackOutboxEventRecorder outboxEventRecorder(EventExternalizationConfiguration configuration, Outbox outbox) {

		LOGGER.debug("Registering domain event externalization via outbox…");

		return new NamastackOutboxEventRecorder(configuration, outbox);
	}
}
