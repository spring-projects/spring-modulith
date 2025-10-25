/*
 * Copyright 2025 the original author or authors.
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

import org.bson.UuidRepresentation;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.mongodb.MongoClientSettings.Builder;

@TestConfiguration(proxyBeanMethods = false)
public class Infrastructure extends AbstractMongoClientConfiguration {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDBContainer() {
		return new MongoDBContainer(DockerImageName.parse("mongo:latest"));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.config.MongoConfigurationSupport#getDatabaseName()
	 */
	@Override
	protected String getDatabaseName() {
		return "test";
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.config.MongoConfigurationSupport#configureClientSettings(com.mongodb.MongoClientSettings.Builder)
	 */
	@Override
	protected void configureClientSettings(Builder builder) {
		builder.uuidRepresentation(UuidRepresentation.STANDARD);
	}
}
