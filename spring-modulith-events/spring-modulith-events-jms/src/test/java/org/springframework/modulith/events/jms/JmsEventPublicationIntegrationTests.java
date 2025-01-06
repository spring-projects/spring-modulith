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
package org.springframework.modulith.events.jms;

import static org.assertj.core.api.Assertions.*;

import lombok.RequiredArgsConstructor;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.Externalized;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for JMS-based event publication.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
@SpringBootTest
class JmsEventPublicationIntegrationTests {

	@Autowired TestPublisher publisher;
	@Autowired EmbeddedActiveMQ artemis;

	@SpringBootApplication
	static class Infrastructure {

		@Bean
		TestPublisher testPublisher(ApplicationEventPublisher publisher) {
			return new TestPublisher(publisher);
		}

		@Bean
		TestListener testListener() {
			return new TestListener();
		}
	}

	@Test // GH-248
	void publishesEventToJmsBroker() throws Exception {

		var server = artemis.getActiveMQServer();
		var before = server.getTotalMessageCount();

		publisher.publishEvent();

		Thread.sleep(400);

		var after = server.getTotalMessageCount();

		assertThat(after - before).isEqualTo(1);
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
