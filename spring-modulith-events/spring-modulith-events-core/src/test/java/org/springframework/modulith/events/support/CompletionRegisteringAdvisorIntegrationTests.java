/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.events.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor.CompletionRegisteringMethodInterceptor;
import org.springframework.scheduling.annotation.AnnotationAsyncExecutionInterceptor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * @author Oliver Drotbohm
 */
@ExtendWith(SpringExtension.class)
class CompletionRegisteringAdvisorIntegrationTests {

	@Autowired SampleListener listener;

	@EnableAsync
	@EnableTransactionManagement
	@Configuration
	static class TestConfiguration {

		@Bean
		SampleListener listener() {
			return new SampleListener();
		}

		@Bean
		PlatformTransactionManager transactionManager() {
			return mock(PlatformTransactionManager.class);
		}

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		static CompletionRegisteringAdvisor completionRegisteringAdvisor() {

			var publicationRegistry = mock(EventPublicationRegistry.class);

			return new CompletionRegisteringAdvisor(() -> publicationRegistry);
		}
	}

	static class SampleListener {

		@Async
		@Transactional
		@TransactionalEventListener
		void on(Object event) {}
	}

	@Test // #118
	void addsCompletionRegisteringInterceptor() throws Exception {

		assertThat(listener).isInstanceOfSatisfying(Advised.class, it -> {

			assertThat(it.getAdvisors())
					.extracting(Advisor::getAdvice)
					.<Class<?>> extracting(Object::getClass)
					.startsWith(
							AnnotationAsyncExecutionInterceptor.class,
							CompletionRegisteringMethodInterceptor.class,
							TransactionInterceptor.class);
		});
	}
}
