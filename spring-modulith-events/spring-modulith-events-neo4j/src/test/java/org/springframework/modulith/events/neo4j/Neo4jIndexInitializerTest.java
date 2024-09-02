/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.modulith.events.neo4j;

import static org.assertj.core.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Gerrit Meier
 */
class Neo4jIndexInitializerTest {

	@ImportAutoConfiguration({ Neo4jEventPublicationAutoConfiguration.class, TestBase.Config.class })
	@Testcontainers(disabledWithoutDocker = true)
	@ContextConfiguration(classes = TestApplication.class)
	static class TestBase {

		@Container private static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5"))
				.withRandomPassword();

		@MockitoBean EventSerializer eventSerializer;

		@Configuration
		static class Config {

			@Bean
			Driver driver() {
				return GraphDatabase.driver(neo4jContainer.getBoltUrl(),
						AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
			}
		}
	}

	@Nested
	@DataNeo4jTest(properties = "spring.modulith.events.neo4j.event-index.enabled=true")
	class WithIndexEnabled extends TestBase {

		@Autowired Neo4jClient neo4jClient;
		@Autowired Optional<Neo4jIndexInitializer> neo4jIndexInitializer;

		@Test
		void indexInitializerBeanIsPresent() {
			assertThat(neo4jIndexInitializer).isPresent();
		}

		@Test
		void indexWasCreated() {
			assertThat(neo4jClient.query("SHOW INDEX YIELD name")
					.fetchAs(String.class)
					.all()).contains("eventHashIndex");
		}
	}

	@Nested
	@DataNeo4jTest(properties = "spring.modulith.events.neo4j.event-index.enabled=false")
	class WithoutIndexEnabled extends TestBase {

		@Autowired Neo4jClient neo4jClient;
		@Autowired Optional<Neo4jIndexInitializer> neo4jIndexInitializer;

		@Test
		void indexInitializerBeanIsNotPresent() {
			assertThat(neo4jIndexInitializer).isEmpty();
		}

		@Test
		void indexWasNotCreated() {
			assertThat(neo4jClient.query("SHOW INDEX YIELD name")
					.fetchAs(String.class)
					.all()).doesNotContain("eventHashIndex");
		}
	}
}
