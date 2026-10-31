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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.modulith.events.core.EventListenerMethodMetadata;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.support.InTransactionCompletionRegisteringAdvisor.InTransactionCompletionRegisteringInterceptor;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit tests for {@link TransactionalCompletionRegisteringInterceptor} and {@link CompletionRegisteringBeanPostProcessor}.
 *
 * @author Oliver Drotbohm
 */
class TransactionalCompletionRegisteringInterceptorUnitTests {

	EventPublicationRegistry registry = mock(EventPublicationRegistry.class);
	TransactionOperations transactions = spy(TransactionOperations.withoutTransaction());
	EventListenerMethodMetadata metadata = EventListenerMethodMetadata.of(MockEnvironment::new);
	CompletionRegisteringAdvisor advisor = new CompletionRegisteringAdvisor(() -> registry, metadata,
			() -> transactions);

	@AfterEach
	void clear() {
		TransactionSynchronizationManager.clear();
	}

	@Test // GH-1961
	void completesPublicationBeforeCommitIfTransactionIsActive() {

		TransactionSynchronizationManager.initSynchronization();

		createProxy().on("event");

		// Not completed on method return, neither by the outer interceptor
		verify(registry).markProcessing(any(), any());
		verify(registry, never()).markCompleted(any(), any());
		
		TransactionSynchronizationManager.getSynchronizations().forEach(it -> it.beforeCommit(false));

		verify(registry).markCompleted(eq("event"), any());
	}

	@Test // GH-1961
	void fallsBackToOuterInterceptorWithoutTransaction() {

		createProxy().on("event");

		verify(registry).markCompleted(eq("event"), any());
		verify(transactions).executeWithoutResult(any());
	}

	@Test // GH-1961
	void addsAdvisorDirectlyAfterTransactionInterceptor() {

		var processor = new CompletionRegisteringBeanPostProcessor(() -> registry, metadata);
		var proxy = (Advised) processor.postProcessAfterInitialization(proxyWithTransactionAdvisor(), "bean");

		assertThat(proxy.getAdvisors())
				.extracting(Advisor::getAdvice)
				.<Class<?>> extracting(Object::getClass)
				.endsWith(TransactionInterceptor.class, InTransactionCompletionRegisteringInterceptor.class);

		// Idempotent
		processor.postProcessAfterInitialization(proxy, "bean");
		assertThat(proxy.getAdvisors()).hasSize(3);
	}

	@Test // GH-1961
	void waitsForFutureOfAsyncListenerAndCompletesBeforeCommit() throws Exception {

		TransactionSynchronizationManager.initSynchronization();

		var event = CompletableFuture.completedFuture("result");
		var result = createProxy(true).future(event);

		assertThat(result.get()).isEqualTo("result");

		TransactionSynchronizationManager.getSynchronizations().forEach(it -> it.beforeCommit(false));

		verify(registry).markCompleted(eq(event), any());
		verify(transactions, never()).executeWithoutResult(any());
	}

	@Test // GH-1961
	void propagatesFailureOfFutureOfAsyncListenerWithoutCompleting() {

		TransactionSynchronizationManager.initSynchronization();

		var failure = new IllegalStateException("Expected!");
		var event = CompletableFuture.<String> failedFuture(failure);

		assertThatIllegalStateException()
				.isThrownBy(() -> createProxy(true).future(event))
				.isSameAs(failure);

		assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
		verify(registry).markFailed(eq(event), any());
		verify(registry, never()).markCompleted(any(), any());
	}

	@Test // GH-1961
	void doesNotCompleteInTransactionForPendingFutureOfSynchronousListener() {

		TransactionSynchronizationManager.initSynchronization();

		var event = new CompletableFuture<String>();
		var result = createProxy(false).future(event);

		assertThat(result).isNotDone();
		assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();

		event.complete("result");

		verify(registry).markCompleted(eq(event), any());
		verify(transactions).executeWithoutResult(any());
	}

	@Test // GH-1961
	void completesBeforeCommitForCompletedFutureOfSynchronousListener() {

		TransactionSynchronizationManager.initSynchronization();

		var event = CompletableFuture.completedFuture("result");

		createProxy(false).future(event);

		TransactionSynchronizationManager.getSynchronizations().forEach(it -> it.beforeCommit(false));

		verify(registry).markCompleted(eq(event), any());
		verify(transactions, never()).executeWithoutResult(any());
	}

	@Test // GH-1961
	void detectsAsyncInterceptorInChain() {

		var factory = new ProxyFactory(new Sample());
		factory.setProxyTargetClass(true);
		factory.addAdvisor(new DefaultPointcutAdvisor(mock(AsyncExecutionInterceptor.class)));
		factory.addAdvisor(advisor);
		factory.addAdvisor(new DefaultPointcutAdvisor(mock(TransactionInterceptor.class)));

		var processor = new CompletionRegisteringBeanPostProcessor(() -> registry, metadata);
		var proxy = (Advised) processor.postProcessAfterInitialization(factory.getProxy(), "bean");

		assertThat(proxy.getAdvisors()[proxy.getAdvisors().length - 1].getAdvice())
				.extracting("async").isEqualTo(true);
	}

	private Object proxyWithTransactionAdvisor() {

		var factory = new ProxyFactory(new Sample());
		factory.setProxyTargetClass(true);
		factory.addAdvisor(advisor);
		factory.addAdvisor(new DefaultPointcutAdvisor(mock(TransactionInterceptor.class)));

		return factory.getProxy();
	}

	private Sample createProxy() {
		return createProxy(false);
	}

	private Sample createProxy(boolean async) {

		var factory = new ProxyFactory(new Sample());
		factory.setProxyTargetClass(true);
		factory.addAdvisor(advisor);
		factory.addAdvisor(new InTransactionCompletionRegisteringAdvisor(() -> registry, metadata, async));

		return (Sample) factory.getProxy();
	}

	static class Sample {

		@TransactionalEventListener
		public void on(Object event) {}

		@TransactionalEventListener
		public CompletableFuture<String> future(CompletableFuture<String> event) {
			return event;
		}
	}
}
