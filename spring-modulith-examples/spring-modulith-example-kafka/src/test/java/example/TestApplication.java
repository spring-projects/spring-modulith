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
package example;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import example.order.Order;
import example.order.OrderManagement;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.messaging.Message;

/**
 * @author Oliver Drotbohm
 */
@SpringBootApplication
public class TestApplication {

	private static final Logger logger = LoggerFactory.getLogger(TestApplication.class);

	@Bean
	@Primary
	@SuppressWarnings("unchecked")
	KafkaOperations<?, ?> kafkaOperations() {

		var mock = mock(KafkaOperations.class);

		when(mock.send(any(Message.class))).then(invocation -> {

			logger.info("Sending message {}.", invocation.getArguments()[0]);

			return CompletableFuture.completedFuture(null);
		});

		return mock;
	}

	public static void main(String[] args) {

		var orders = SpringApplication.run(TestApplication.class, args)
				.getBean(OrderManagement.class);

		logger.info("Triggering order completion…");

		orders.complete(new Order());
	}
}
