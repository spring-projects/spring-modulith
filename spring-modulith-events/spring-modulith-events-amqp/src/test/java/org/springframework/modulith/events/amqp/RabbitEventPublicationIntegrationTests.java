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

import static org.assertj.core.api.Assertions.*;

import lombok.RequiredArgsConstructor;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.Externalized;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for RabbitMQ-based event publication.
 *
 * @author Oliver Drotbohm
 */
@SpringBootTest
class RabbitEventPublicationIntegrationTests {

	@Autowired TestPublisher publisher;
	@Autowired RabbitAdmin rabbit;
	@Autowired CompletedEventPublications completed;

	@SpringBootApplication
	static class TestConfiguration {

		@Bean
		@ServiceConnection
		RabbitMQContainer rabbitMqContainer() {
			return new RabbitMQContainer(DockerImageName.parse("rabbitmq"));
		}

		@Bean
		TestPublisher testPublisher(ApplicationEventPublisher publisher) {
			return new TestPublisher(publisher);
		}

		@Bean
		TestListener testListener() {
			return new TestListener();
		}
	}

	@Test
	void publishesEventToRabbitMq() throws Exception {

		var target = new FanoutExchange("target");
		rabbit.declareExchange(target);

		var queue = new Queue("queue");
		rabbit.declareQueue(queue);

		Binding binding = BindingBuilder.bind(queue).to(target);
		rabbit.declareBinding(binding);

		publisher.publishEvent();

		Thread.sleep(200);

		var info = rabbit.getQueueInfo("queue");

		assertThat(info.getMessageCount()).isEqualTo(1);
		assertThat(completed.findAll()).hasSize(1);
	}

	@Externalized("target")
	static class TestEvent {}

	@RequiredArgsConstructor
	static class TestPublisher {

		private final ApplicationEventPublisher events;

		@Transactional
		void publishEvent() {
			events.publishEvent(new TestEvent());
		}
	}

	static class TestListener {

		@ApplicationModuleListener
		void on(TestEvent event) {}
	}
}
