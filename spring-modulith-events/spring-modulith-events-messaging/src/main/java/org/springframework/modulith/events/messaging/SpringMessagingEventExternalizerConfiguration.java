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
package org.springframework.modulith.events.messaging;

import io.namastack.outbox.handler.OutboxHandler;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.outbox.ExternalizationMode;
import org.springframework.modulith.events.outbox.OutboxEventTransport;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

/**
 * Auto-configuration to set up a {@link DelegatingEventExternalizer} to externalize events to a Spring Messaging
 * {@link MessageChannel}.
 *
 * @author Josh Long
 * @author Oliver Drotbohm
 */
@AutoConfiguration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnClass(MessageChannel.class)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true",
		matchIfMissing = true)
class SpringMessagingEventExternalizerConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SpringMessagingEventExternalizerConfiguration.class);

	public static final String MODULITH_ROUTING_HEADER = "springModulith_routingTarget";

	@Bean
	@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "module-listener", matchIfMissing = true)
	DelegatingEventExternalizer springMessagingEventExternalizer(EventExternalizationConfiguration configuration,
			BeanFactory factory) {

		logger.debug("Registering domain event externalization for Spring Messaging…");

		return new DelegatingEventExternalizer(configuration,
				createMessagingTransport(configuration, factory));
	}

	@ConditionalOnClass(OutboxHandler.class)
	static class NamastackOutboxAutoConfiguration {

		@Bean
		@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "outbox")
		OutboxHandler springMessagingOutboxExternalizer(EventExternalizationConfiguration configuration,
				BeanFactory factory) {

			logger.debug("Registering domain event outbox externalization for Spring Messaging…");

			return new OutboxEventTransport(configuration,
					createMessagingTransport(configuration, factory));
		}
	}

	private static BiFunction<RoutingTarget, Object, CompletableFuture<?>> createMessagingTransport(
			EventExternalizationConfiguration configuration, BeanFactory factory) {

		var context = new StandardEvaluationContext();
		context.setBeanResolver(new BeanFactoryResolver(factory));

		return (target, payload) -> {

			var targetChannel = BrokerRouting.of(target, context).getTarget(payload);
			var message = MessageBuilder
					.withPayload(payload)
					.setHeader(MODULITH_ROUTING_HEADER, target.toString())
					.copyHeadersIfAbsent(configuration.getHeadersFor(payload))
					.build();

			if (logger.isDebugEnabled()) {
				logger.debug("trying to find a {} with name {}", MessageChannel.class.getName(), targetChannel);
			}

			factory.getBean(targetChannel, MessageChannel.class).send(message);

			return CompletableFuture.completedFuture(null);
		};
	}
}
