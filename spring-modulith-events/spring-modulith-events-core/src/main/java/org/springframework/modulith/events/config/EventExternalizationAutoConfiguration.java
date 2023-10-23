/*
 * Copyright 2023 the original author or authors.
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

import java.lang.reflect.Method;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Ordered;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

/**
 * Auto-configuration to externalize application events.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled",
		havingValue = "true",
		matchIfMissing = true)
@AutoConfiguration
@AutoConfigureAfter(EventPublicationConfiguration.class)
public class EventExternalizationAutoConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static EventListenerFactory filteringEventListenerFactory(EventExternalizationConfiguration config) {
		return new ConditionalTransactionalEventListenerFactory(config);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@ConditionalOnMissingBean
	static EventExternalizationConfiguration eventExternalizationConfiguration(BeanFactory factory) {

		var packages = AutoConfigurationPackages.get(factory);

		return EventExternalizationConfiguration.defaults(packages).build();
	}

	/**
	 * A custom {@link EventListenerFactory} to create {@link ConditionalTransactionalApplicationListenerMethodAdapter}
	 * instances.
	 *
	 * @author Oliver Drotbohm
	 */
	private static final class ConditionalTransactionalEventListenerFactory
			extends TransactionalEventListenerFactory implements Ordered {

		private final EventExternalizationConfiguration config;

		/**
		 * Creates a new {@link ConditionalTransactionalEventListenerFactory} for the given
		 * {@link EventExternalizationConfiguration}.
		 *
		 * @param config must not be {@literal null}.
		 */
		private ConditionalTransactionalEventListenerFactory(EventExternalizationConfiguration config) {
			this.config = config;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.transaction.event.TransactionalEventListenerFactory#supportsMethod(java.lang.reflect.Method)
		 */
		@Override
		public boolean supportsMethod(Method method) {
			return super.supportsMethod(method)
					&& ConditionalEventListener.class.isAssignableFrom(method.getDeclaringClass());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.transaction.event.TransactionalEventListenerFactory#createApplicationListener(java.lang.String, java.lang.Class, java.lang.reflect.Method)
		 */
		@Override
		public ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method) {
			return new ConditionalTransactionalApplicationListenerMethodAdapter(beanName, type, method, config);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.transaction.event.TransactionalEventListenerFactory#getOrder()
		 */
		@Override
		public int getOrder() {
			return 25;
		}
	}

	/**
	 * A custom {@link TransactionalApplicationListenerMethodAdapter} that also implements
	 * {@link ConditionalEventListener} so that the adapter can be filtered out based on the event to be published.
	 *
	 * @author Oliver Drotbohm
	 * @see ConditionalEventListener
	 * @see PersistentApplicationEventMulticaster
	 */
	private static class ConditionalTransactionalApplicationListenerMethodAdapter
			extends TransactionalApplicationListenerMethodAdapter
			implements ConditionalEventListener {

		private final EventExternalizationConfiguration configuration;

		/**
		 * @param beanName
		 * @param targetClass
		 * @param method
		 * @param configuration
		 */
		ConditionalTransactionalApplicationListenerMethodAdapter(String beanName, Class<?> targetClass, Method method,
				EventExternalizationConfiguration configuration) {
			super(beanName, targetClass, method);
			this.configuration = configuration;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.ConditionalEventListener#supports(java.lang.Object)
		 */
		@Override
		public boolean supports(Object event) {
			return configuration.supports(event);
		}
	}
}
