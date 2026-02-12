/*
 * Copyright 2024-2026 the original author or authors.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.Externalized;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for Spring Messaging-based event publication.
 *
 * @author Josh Long
 * @author Oliver Drotbohm
 * @since 1.3
 */
@SpringBootTest
class SpringMessagingEventPublicationIntegrationTests {

	private static final String TARGET = "target::#{someExpression}";
	private static final String CHANNEL_NAME = "target";
	private static final AtomicInteger COUNTER = new AtomicInteger();

	@Autowired TestPublisher publisher;
	@Autowired CompletedEventPublications completed;

	@SpringBootApplication
	static class TestConfiguration {

		@Bean
		TestPublisher testPublisher(ApplicationEventPublisher publisher) {
			return new TestPublisher(publisher);
		}

		@Bean
		IntegrationFlow inboundIntegrationFlow(@Qualifier(CHANNEL_NAME) MessageChannel inbound) {

			return IntegrationFlow
					.from(inbound)
					.handle((__, headers) -> {

						assertThat(headers.get(SpringMessagingEventExternalizerConfiguration.MODULITH_ROUTING_HEADER))
								.isEqualTo(TARGET);

						COUNTER.incrementAndGet();
						return null;
					})
					.get();
		}

		@Bean(value = CHANNEL_NAME)
		DirectChannelSpec target() {
			return MessageChannels.direct();
		}

	}

	@Test
	void publishesEventToSpringMessaging() throws Exception {

		var publishes = 2;

		for (var i = 0; i < publishes; i++) {
			publisher.publishEvent();
		}

		Thread.sleep(200);

		assertThat(COUNTER.get()).isEqualTo(publishes);
		assertThat(completed.findAll()).hasSize(publishes);
	}

	@Externalized(TARGET)
	static class TestEvent {}

	@RequiredArgsConstructor
	static class TestPublisher {

		private final ApplicationEventPublisher events;

		@Transactional
		void publishEvent() {
			events.publishEvent(new TestEvent());
		}
	}
}
