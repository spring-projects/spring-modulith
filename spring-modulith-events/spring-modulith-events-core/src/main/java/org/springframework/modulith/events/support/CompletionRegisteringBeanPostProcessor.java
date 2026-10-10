/*
 * Copyright 2026 the original author or authors.
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

import java.util.function.Supplier;

import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.modulith.events.core.EventListenerMethodMetadata;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor.CompletionRegisteringMethodInterceptor;
import org.springframework.modulith.events.support.InTransactionCompletionRegisteringAdvisor.InTransactionCompletionRegisteringInterceptor;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.Assert;

/**
 * A {@link BeanPostProcessor} that adds an advisor completing event publications right before commit to the advisor
 * chain of {@link Advised} beans, directly after the {@link TransactionInterceptor}. Only beans that already carry the
 * {@link CompletionRegisteringMethodInterceptor} are considered.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
public class CompletionRegisteringBeanPostProcessor implements BeanPostProcessor, Ordered {

	private final Supplier<EventPublicationRegistry> registry;
	private final EventListenerMethodMetadata metadata;

	/**
	 * Creates a new {@link CompletionRegisteringBeanPostProcessor} for the given (lazily resolved)
	 * {@link EventPublicationRegistry} and {@link EventListenerMethodMetadata}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 */
	public CompletionRegisteringBeanPostProcessor(Supplier<EventPublicationRegistry> registry,
			EventListenerMethodMetadata metadata) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");
		Assert.notNull(metadata, "EventListenerMethodMetadata must not be null!");

		this.registry = registry;
		this.metadata = metadata;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {

		if (!(bean instanceof Advised advised) || advised.isFrozen()) {
			return bean;
		}

		var advisors = advised.getAdvisors();
		var hasCompletion = false;
		var transactionIndex = -1;
		var async = false;

		for (int i = 0; i < advisors.length; i++) {

			var advice = advisors[i].getAdvice();

			if (advice instanceof AsyncExecutionInterceptor) {
				async = true;
			} else if (advice instanceof CompletionRegisteringMethodInterceptor) {
				hasCompletion = true;
			} else if (advice instanceof InTransactionCompletionRegisteringInterceptor) {
				return bean; // already processed
			} else if (advice instanceof TransactionInterceptor) {
				transactionIndex = i;
			}
		}

		if (hasCompletion && transactionIndex >= 0) {

			advised.addAdvisor(transactionIndex + 1,
					new InTransactionCompletionRegisteringAdvisor(registry, metadata, async));
		}

		return bean;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
