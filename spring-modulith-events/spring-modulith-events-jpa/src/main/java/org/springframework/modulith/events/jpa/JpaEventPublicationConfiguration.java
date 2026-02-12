/*
 * Copyright 2017-2026 the original author or authors.
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
package org.springframework.modulith.events.jpa;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * @author Oliver Drotbohm
 * @author Dmitry Belyaev
 * @author Björn Kieling
 */
@Configuration(proxyBeanMethods = false)
class JpaEventPublicationConfiguration implements EventPublicationConfigurationExtension {

	@Bean
	JpaEventPublicationRepository jpaEventPublicationRepository(EntityManager em, EventSerializer serializer,
			Environment environment) {
		return new JpaEventPublicationRepository(em, serializer, CompletionMode.from(environment));
	}
}
