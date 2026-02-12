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
package org.springframework.modulith.events.mongodb;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * Autoconfiguration for MongoDB event publication repository.
 *
 * @author Oliver Drotbohm
 */
@AutoConfiguration
@AutoConfigureBefore(EventPublicationAutoConfiguration.class)
class MongoDbEventPublicationAutoConfiguration implements EventPublicationConfigurationExtension {

	@Bean
	MongoDbEventPublicationRepository mongoDbEventPublicationRepository(MongoTemplate template, Environment environment) {
		return new MongoDbEventPublicationRepository(template, CompletionMode.from(environment));
	}
}
