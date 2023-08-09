/*
 * Copyright 2022-2023 the original author or authors.
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

import example.inventory.InventoryUpdated;
import example.order.OrderManagement;

import java.util.Collection;

import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * Integration test for the overall application.
 *
 * @author Oliver Drotbohm
 */
@SpringBootTest
@EnableScenarios
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIntegrationTests {

	@TestConfiguration
	static class MongoDbInfrastructureConfiguration {

		@Bean
		@ServiceConnection
		MongoDBContainer mongoDBContainer() {
			return new MongoDBContainer("mongo:6.0.3");
		}

		@Bean
		public MongoClient mongoClient(MongoDBContainer container) {

			var settings = MongoClientSettings.builder()
					.uuidRepresentation(UuidRepresentation.STANDARD)
					.applyConnectionString(new ConnectionString(container.getReplicaSetUrl()))
					.build();

			return MongoClients.create(settings);
		}
	}

	@Autowired OrderManagement orders;
	@Autowired EventPublicationRegistry registry;

	@Test
	void bootstrapsApplication(Scenario scenario) throws Exception {

		scenario.stimulate(() -> orders.complete())
				.andWaitForStateChange(() -> registry.findIncompletePublications(), Collection::isEmpty)
				.andExpect(InventoryUpdated.class)
				.toArrive();
	}
}
