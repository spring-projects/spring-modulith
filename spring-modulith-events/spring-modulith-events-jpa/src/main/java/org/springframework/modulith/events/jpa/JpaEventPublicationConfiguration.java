/*
 * Copyright 2017-2022 the original author or authors.
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
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.config.EventPublicationConfigurationExtension;

import lombok.RequiredArgsConstructor;

/**
 * @author Oliver Drotbohm, Dmitry Belyaev, Björn Kieling
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class JpaEventPublicationConfiguration implements EventPublicationConfigurationExtension {

	@Bean
	public JpaEventPublicationRepository jpaEventPublicationRepository(EntityManager em, EventSerializer serializer) {
		// TODO Why do we want to instantiate the serializer here and what
		// happens if no serializer is available?
		return new JpaEventPublicationRepository(em, serializer);
	}
}
