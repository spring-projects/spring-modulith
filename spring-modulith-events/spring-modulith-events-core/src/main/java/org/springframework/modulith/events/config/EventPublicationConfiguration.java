/*
 * Copyright 2017-2023 the original author or authors.
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
package org.springframework.modulith.events.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor;
import org.springframework.modulith.events.support.PersistentApplicationEventMulticaster;

/**
 * Fundamental configuration for the {@link EventPublicationRegistry} support.
 *
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
@Configuration(proxyBeanMethods = false)
class EventPublicationConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	EventPublicationRegistry eventPublicationRegistry(EventPublicationRepository repository,
			ObjectProvider<Clock> clock) {
		return new DefaultEventPublicationRegistry(repository, clock.getIfAvailable(() -> Clock.systemUTC()));
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static PersistentApplicationEventMulticaster applicationEventMulticaster(
			ObjectFactory<EventPublicationRegistry> eventPublicationRegistry, ObjectFactory<Environment> environment) {

		return new PersistentApplicationEventMulticaster(() -> eventPublicationRegistry.getObject(),
				() -> environment.getObject());
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static CompletionRegisteringAdvisor completionRegisteringAdvisor(ObjectFactory<EventPublicationRegistry> registry) {
		return new CompletionRegisteringAdvisor(registry::getObject);
	}
}
