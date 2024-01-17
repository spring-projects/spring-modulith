/*
 * Copyright 2019-2024 the original author or authors.
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
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Unit tests for {@link CompletionRegisteringBeanPostProcessor}.
 *
 * @author Oliver Drotbohm
 */
class CompletionRegisteringAdvisorUnitTests {

	EventPublicationRegistry registry = mock(EventPublicationRegistry.class);
	SomeEventListener bean = new SomeEventListener();

	@Test
	void triggersCompletionForAfterCommitEventListener() throws Exception {
		assertCompletion(SomeEventListener::onAfterCommit);
	}

	@Test
	void doesNotTriggerCompletionForNonAfterCommitPhase() throws Exception {
		assertNonCompletion(SomeEventListener::onAfterRollback);
	}

	@Test
	void doesNotTriggerCompletionForPlainEventListener() {
		assertNonCompletion(SomeEventListener::simpleEventListener);
	}

	@Test
	void doesNotTriggerCompletionForNonEventListener() {
		assertNonCompletion(SomeEventListener::nonEventListener);
	}

	@Test // GH-395
	void doesNotTriggerCompletionOnFailedCompletableFuture() throws Throwable {

		var result = createProxyFor(bean).asyncWithResult(true);

		assertThat(result.isDone()).isFalse();
		verify(registry, never()).markCompleted(any(), any());

		Thread.sleep(500);

		assertThat(result.isCompletedExceptionally()).isTrue();
		verify(registry, never()).markCompleted(any(), any());
	}

	@Test // GH-395
	void marksLazilyComputedCompletableFutureAsCompleted() throws Throwable {

		var result = createProxyFor(bean).asyncWithResult(false);

		assertThat(result.isDone()).isFalse();
		verify(registry, never()).markCompleted(any(), any());

		Thread.sleep(500);

		assertThat(result.isCompletedExceptionally()).isFalse();
		verify(registry).markCompleted(any(), any());
	}

	private void assertCompletion(BiConsumer<SomeEventListener, Object> consumer) {
		assertCompletion(consumer, true);
	}

	private void assertNonCompletion(BiConsumer<SomeEventListener, Object> consumer) {
		assertCompletion(consumer, false);
	}

	private void assertCompletion(BiConsumer<SomeEventListener, Object> consumer, boolean expected) {

		Object processed = createProxyFor(bean);

		assertThat(processed).isInstanceOf(Advised.class);
		assertThat(processed).isInstanceOfSatisfying(SomeEventListener.class, //
				it -> consumer.accept(it, new Object()));

		verify(registry, times(expected ? 1 : 0)).markCompleted(any(), any());
	}

	@SuppressWarnings("unchecked")
	private <T> T createProxyFor(T bean) {

		ProxyFactory factory = new ProxyFactory(bean);
		factory.addAdvisor(new CompletionRegisteringAdvisor(() -> registry));
		return (T) factory.getProxy();
	}

	static class SomeEventListener {

		@TransactionalEventListener
		void onAfterCommit(Object event) {}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
		void onAfterRollback(Object object) {}

		@EventListener
		void simpleEventListener(Object object) {}

		void nonEventListener(Object object) {}

		@Async
		@TransactionalEventListener
		CompletableFuture<?> asyncWithResult(boolean fail) {

			return CompletableFuture.completedFuture(new Object())
					.thenComposeAsync(it -> {

						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {}

						return fail
								? CompletableFuture.failedFuture(new IllegalArgumentException())
								: CompletableFuture.completedFuture(it);
					});
		}
	}
}
