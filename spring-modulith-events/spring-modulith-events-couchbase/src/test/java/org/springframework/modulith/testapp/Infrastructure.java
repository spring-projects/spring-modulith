/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.modulith.testapp;

import static com.couchbase.client.core.io.CollectionIdentifier.*;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.utility.DockerImageName;

import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;

/**
 * @author Oliver Drotbohm
 * @author Alexandre Vigneron
 */
@TestConfiguration(proxyBeanMethods = false)
public class Infrastructure {
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

	@Bean
	JsonMapper jsonMapper() {
		return JsonMapper.builder()
				.build();
	}
}
