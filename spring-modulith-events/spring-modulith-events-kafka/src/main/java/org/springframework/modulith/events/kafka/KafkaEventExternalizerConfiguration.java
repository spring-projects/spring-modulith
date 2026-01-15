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

import io.namastack.outbox.handler.OutboxHandler;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.jspecify.annotations.NullUnmarked;
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
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.outbox.ExternalizationMode;
import org.springframework.modulith.events.outbox.OutboxEventTransport;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.modulith.events.support.DelegatingEventExternalizer;

/**
 * Auto-configuration to set up a {@link DelegatingEventExternalizer} to externalize events to Kafka.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */

@AutoConfiguration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled",
		havingValue = "true",
		matchIfMissing = true)
class KafkaEventExternalizerConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(KafkaEventExternalizerConfiguration.class);

	@Bean
	@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "module-listener", matchIfMissing = true)
	DelegatingEventExternalizer kafkaEventExternalizer(EventExternalizationConfiguration configuration,
			KafkaOperations<?, ?> operations, BeanFactory factory) {

		logger.debug("Registering domain event externalization to Kafka…");

		return new DelegatingEventExternalizer(configuration,
				createKafkaTransport(configuration, operations, factory));
	}

	@ConditionalOnClass(OutboxHandler.class)
	static class NamastackOutboxAutoConfiguration {

		@Bean
		@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "outbox")
		OutboxHandler kafkaOutboxExternalizer(EventExternalizationConfiguration configuration,
				KafkaOperations<?, ?> operations, BeanFactory factory) {

			logger.debug("Registering domain event outbox externalization to Kafka…");

			return new OutboxEventTransport(configuration,
					createKafkaTransport(configuration, operations, factory));
		}
	}

	@NullUnmarked // Until https://github.com/spring-projects/spring-framework/issues/36157 is resolved
	private static BiFunction<RoutingTarget, Object, CompletableFuture<?>> createKafkaTransport(
			EventExternalizationConfiguration configuration, KafkaOperations<?, ?> operations,
			BeanFactory factory) {

		var context = new StandardEvaluationContext();
		context.setBeanResolver(new BeanFactoryResolver(factory));

		return (target, payload) -> {

			var routing = BrokerRouting.of(target, context);

			var builder = payload instanceof Message<?> message
					? MessageBuilder.fromMessage(message)
					: MessageBuilder.withPayload(payload).copyHeaders(configuration.getHeadersFor(payload));

			var message = builder
					.setHeaderIfAbsent(KafkaHeaders.KEY, routing.getKey(payload))
					.setHeaderIfAbsent(KafkaHeaders.TOPIC, routing.getTarget(payload))
					.build();

			return operations.send(message);
		};
	}
}
