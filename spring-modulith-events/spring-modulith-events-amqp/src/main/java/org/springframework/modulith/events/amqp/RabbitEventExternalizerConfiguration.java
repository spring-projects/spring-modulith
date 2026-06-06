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
package org.springframework.modulith.events.amqp;

import io.namastack.outbox.handler.OutboxHandler;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.modulith.events.support.EventExternalizationTransport;
import org.springframework.modulith.events.support.EventExternalizerModuleListener;
import org.springframework.modulith.events.support.OutboxEventExternalizer;
import org.springframework.modulith.events.support.OutboxEventExternalizerFactory;
import org.springframework.util.Assert;

/**
 * Auto-configuration to set up a {@link org.springframework.modulith.events.support.DelegatingEventExternalizer} to
 * externalize events to RabbitMQ.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
@AutoConfiguration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled",
		havingValue = "true",
		matchIfMissing = true)
class RabbitEventExternalizerConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(RabbitEventExternalizerConfiguration.class);
	private static final String PUBLISHER_CONFIRM_TYPE = "spring.rabbitmq.publisher-confirm-type";

	@Bean
	@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "module-listener", matchIfMissing = true)
	EventExternalizerModuleListener rabbitEventExternalizer(EventExternalizationConfiguration configuration,
			RabbitMessageOperations operations, BeanFactory factory) {

		logger.debug("Registering domain event externalization to RabbitMQ…");

		return new EventExternalizerModuleListener(configuration,
				createRabbitTransport(configuration, operations, factory));
	}

	@AutoConfiguration
	@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "outbox")
	static class RabbitOutboxConfiguration {

		private final OutboxEventExternalizer externalizer;

		RabbitOutboxConfiguration(EventExternalizationConfiguration configuration, RabbitMessageOperations operations,
				BeanFactory beanFactory, OutboxEventExternalizerFactory factory, Environment environment) {

			Assert.state("correlated".equalsIgnoreCase(environment.getProperty(PUBLISHER_CONFIRM_TYPE)),
					() -> "RabbitMQ outbox event externalization requires " + PUBLISHER_CONFIRM_TYPE + "=correlated!");

			this.externalizer = factory.forTransport(createConfirmingRabbitTransport(configuration, operations, beanFactory));
		}

		@AutoConfiguration
		@ConditionalOnClass(OutboxHandler.class)
		class NamastackOutboxAutoConfiguration {

			@Bean
			OutboxHandler kafkaOutboxExternalizer() {

				logger.debug("Registering Namastack domain event outbox externalization to RabbitMQ.");

				return (payload, metadata) -> externalizer.externalizeBlocking(payload);
			}
		}

		@AutoConfiguration
		@ConditionalOnClass({ JobScheduler.class, JobRunrExternalizationTransport.class })
		class JobRunrOutboxAutoConfiguration {

			@Bean
			JobRunrExternalizationTransport jobRunrOutboxExternalizer() {

				logger.debug("Registering JobRunr domain event outbox externalization to RabbitMQ.");

				return payload -> externalizer.externalizeBlocking(payload);
			}
		}
	}

	private static EventExternalizationTransport createRabbitTransport(
			EventExternalizationConfiguration configuration, RabbitMessageOperations operations,
			BeanFactory factory) {

		var context = new StandardEvaluationContext();
		context.setBeanResolver(new BeanFactoryResolver(factory));

		return (payload, target) -> {

			var routing = BrokerRouting.of(target, context);
			var headers = configuration.getHeadersFor(payload);

			operations.convertAndSend(routing.getTarget(payload), routing.getKey(payload), payload, headers);

			return CompletableFuture.completedFuture(null);
		};
	}

	private static EventExternalizationTransport createConfirmingRabbitTransport(
			EventExternalizationConfiguration configuration, RabbitMessageOperations operations,
			BeanFactory factory) {

		var context = new StandardEvaluationContext();
		context.setBeanResolver(new BeanFactoryResolver(factory));

		return (payload, target) -> {

			var routing = BrokerRouting.of(target, context);

			var correlation = new CorrelationData();
			var headers = new HashMap<>(configuration.getHeadersFor(payload));

			headers.put(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION, correlation);

			operations.convertAndSend(routing.getTarget(payload), routing.getKey(payload), payload, headers);

			return correlation.getFuture().thenAccept(confirm -> {

				if (!confirm.ack()) {
					throw new IllegalStateException("RabbitMQ publisher confirm was nacked: "
							+ (confirm.reason() != null ? confirm.reason() : "no reason"));
				}
			});
		};
	}
}
