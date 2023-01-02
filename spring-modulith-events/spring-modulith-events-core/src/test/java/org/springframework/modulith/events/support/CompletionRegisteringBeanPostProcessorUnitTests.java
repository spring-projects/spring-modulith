/*
 * Copyright 2019-2023 the original author or authors.
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

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.EventPublicationRegistry;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Unit tests for {@link CompletionRegisteringBeanPostProcessor}.
 *
 * @author Oliver Drotbohm
 */
class CompletionRegisteringBeanPostProcessorUnitTests {

	EventPublicationRegistry registry = mock(EventPublicationRegistry.class);
	BeanPostProcessor processor = new CompletionRegisteringBeanPostProcessor(() -> registry);
	SomeEventListener bean = new SomeEventListener();

	@Test
	void doesNotProxyNonTransactionalEventListenerClass() {

		NoEventListener bean = new NoEventListener();

		assertThat(bean).isSameAs(processor.postProcessBeforeInitialization(bean, "bean"));
	}

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

	private void assertCompletion(BiConsumer<SomeEventListener, Object> consumer) {
		assertCompletion(consumer, true);
	}

	private void assertNonCompletion(BiConsumer<SomeEventListener, Object> consumer) {
		assertCompletion(consumer, false);
	}

	private void assertCompletion(BiConsumer<SomeEventListener, Object> consumer, boolean expected) {

		Object processed = processor.postProcessAfterInitialization(bean, "listener");

		assertThat(processed).isInstanceOf(Advised.class);
		assertThat(processed).isInstanceOfSatisfying(SomeEventListener.class, //
				it -> consumer.accept(it, new Object()));

		verify(registry, times(expected ? 1 : 0)).markCompleted(any(), any());
	}

	static class SomeEventListener {

		@TransactionalEventListener
		void onAfterCommit(Object event) {}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
		void onAfterRollback(Object object) {}

		@EventListener
		void simpleEventListener(Object object) {}

		void nonEventListener(Object object) {}
	}

	static class NoEventListener {}
}
