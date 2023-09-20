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

import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @author Oliver Drotbohm
 * @author Gerrit Meier
 */
@SpringBootTest
@EnableScenarios
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIntegrationTests {

	@TestConfiguration
	static class InfrastructureConfiguration {

		@Bean
		@ServiceConnection
		Neo4jContainer<?> neo4jContainer() {
			return new Neo4jContainer<>("neo4j:5").withRandomPassword();
		}

		@Bean
		Driver driver(Neo4jContainer<?> container) {
			return GraphDatabase.driver(container.getBoltUrl(), AuthTokens.basic("neo4j", container.getAdminPassword()));
		}

		@Bean
		Configuration cypherDslConfiguration() {
			return Configuration.newConfig().withDialect(Dialect.NEO4J_5).build();
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
