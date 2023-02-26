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

import java.time.Duration;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties.Shutdown;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.EventPublicationRegistry;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor;
import org.springframework.modulith.events.support.PersistentApplicationEventMulticaster;

/**
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
@Configuration(proxyBeanMethods = false)
class EventPublicationConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	EventPublicationRegistry eventPublicationRegistry(EventPublicationRepository repository) {
		return new DefaultEventPublicationRegistry(repository);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static PersistentApplicationEventMulticaster applicationEventMulticaster(
			ObjectFactory<EventPublicationRegistry> eventPublicationRegistry) {
		return new PersistentApplicationEventMulticaster(() -> eventPublicationRegistry.getObject());
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static CompletionRegisteringAdvisor completionRegisteringAdvisor(ObjectFactory<EventPublicationRegistry> registry) {
		return new CompletionRegisteringAdvisor(registry::getObject);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnProperty(
			name = "spring.modulith.default-async-termination",
			havingValue = "true",
			matchIfMissing = true)
	static AsyncPropertiesDefaulter asyncPropertiesDefaulter(Environment environment) {
		return new AsyncPropertiesDefaulter(environment);
	}

	static class AsyncPropertiesDefaulter implements BeanPostProcessor {

		private static final Logger LOGGER = LoggerFactory.getLogger(AsyncPropertiesDefaulter.class);
		private static final String PROPERTY = "spring.task.execution.shutdown.await-termination";

		private final Environment environment;

		AsyncPropertiesDefaulter(Environment environment) {
			this.environment = environment;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
		 */
		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

			if (!(bean instanceof TaskExecutionProperties p)) {
				return bean;
			}

			if (anyPropertyConfigured(PROPERTY, PROPERTY + "-period")) {
				return bean;
			}

			LOGGER.debug("Defaulting async shutdown to await termination in 2 seconds.");

			Shutdown shutdown = p.getShutdown();

			shutdown.setAwaitTermination(true);
			shutdown.setAwaitTerminationPeriod(Duration.ofSeconds(2));

			return p;
		}

		private boolean anyPropertyConfigured(String... properties) {

			return Arrays.stream(properties)
					.map(it -> environment.getProperty(it, (String) null))
					.anyMatch(it -> it != null);
		}
	}
}
