/*
 * Copyright 2022-2026 the original author or authors.
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

import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import example.inventory.InventoryUpdated;
import example.order.OrderManagement;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.testcontainers.utility.DockerImageName;

import static com.couchbase.client.core.io.CollectionIdentifier.DEFAULT_SCOPE;

/**
 * Integration test for the overall application.
 *
 * @author Oliver Drotbohm
 * @author Alexandre Vigneron
 */
@SpringBootTest
@EnableScenarios
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIntegrationTests {

	public static void main(String[] args) {

		SpringApplication.from(Application::main)
				.with(CouchbaseInfrastructureConfiguration.class)
				.run(args)
				.getApplicationContext();
	}

	@TestConfiguration
	static class CouchbaseInfrastructureConfiguration {
		private static final String COUCHBASE_USER = "user";
		private static final String COUCHBASE_PASSWORD = "password";
		private static final String BASE_COLLECTION = "EVENT_PUBLICATION";
		private static final String ARCHIVE_COLLECTION = "EVENT_PUBLICATION_ARCHIVE";

		@Value("${spring.data.couchbase.bucket-name}")
		private String bucketName;

		@Bean
		@ServiceConnection
		CouchbaseContainer couchbaseContainer() {
			var container = new CouchbaseContainer(DockerImageName.parse("couchbase/server:latest"))
					.withBucket(new BucketDefinition(bucketName).withPrimaryIndex(false))
					.withCredentials(COUCHBASE_USER, COUCHBASE_PASSWORD);

			container.start();

			var seedNodes = Set.of(
					SeedNode.create(container.getHost(), Optional.of(container.getBootstrapCarrierDirectPort()), Optional.of(container.getBootstrapHttpDirectPort())
					));
			try (var cluster = Cluster.connect(seedNodes, ClusterOptions.clusterOptions(container.getUsername(), container.getPassword()))) {
				cluster.bucket(bucketName).collections().createCollection(DEFAULT_SCOPE, BASE_COLLECTION);
				cluster.bucket(bucketName).collections().createCollection(DEFAULT_SCOPE, ARCHIVE_COLLECTION);
				cluster.waitUntilReady(Duration.ofMinutes(2));
			}

			return container;
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
