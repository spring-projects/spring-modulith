/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.modulith.events.mongodb;

import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * Setup of embedded MongoDB as Spring beans so that it will properly bound to and shut down with an
 * {@link ApplicationContext}.
 *
 * @author Oliver Drotbohm
 */
@ContextConfiguration(classes = WithEmbeddedMongoDb.TestConfiguration.class)
abstract class WithEmbeddedMongoDb {

	@Configuration
	static class TestConfiguration {

		@Bean
		Net mongoDbConfig() throws Exception {
			return new Net("localhost", Network.freeServerPort(Network.getLocalHost()), false);
		}

		@Bean(destroyMethod = "stop")
		MongodProcess mongoDbProcess(Net config) throws Exception {

			ImmutableMongodConfig mongodConfig = MongodConfig.builder()
					.version(Version.Main.PRODUCTION)
					.net(config)
					.build();

			return MongodStarter.getDefaultInstance()
					.prepare(mongodConfig)
					.start();
		}

		@Bean
		// Artificially depend on MongodProcess so that it will not shut down prior
		// to the repository -> template -> client -> process shutdown chain
		MongoClient mongoDbClient(Net config, MongodProcess process) {
			return MongoClients.create("mongodb://" + config.getBindIp() + ":" + config.getPort());
		}
	}
}
