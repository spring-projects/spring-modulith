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
package org.springframework.modulith.test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import lombok.RequiredArgsConstructor;

import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.modulith.test.PublishedEventsAssert.PublishedEventAssert;
import org.springframework.modulith.test.Scenario.When;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Unit tests for {@link Scenario}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith(MockitoExtension.class)
class ScenarioUnitTests {

	private static final Duration DELAY = Duration.ofMillis(50);
	private static final Duration WAIT_TIME = Duration.ofMillis(101);
	private static final Duration TIMED_OUT = Duration.ofMillis(150);

	@Mock ApplicationEventPublisher publisher;
	TransactionOperations tx = StubTransactionOperations.INSTANCE;

	@Test // GH-136
	void timesOutIfNoEventArrivesInTime() throws Throwable {

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(String.class)
				.toArrive();

		givenAScenario(consumer)
				.waitFor(TIMED_OUT)
				.onEvent(() -> "foo")
				.expectFailure();
	}

	@Test // GH-136
	void failsIfNoEventOfExpectedTypeArrives() {

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(String.class)
				.toArrive();

		givenAScenario(consumer)
				.onEvent(() -> 1L)
				.expectFailure();
	}

	@Test // GH-136
	void matchesEventsWithPredicate() throws Throwable {

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(SomeEvent.class)
				.matching(__ -> __.payload().startsWith("foo"))
				.toArrive();

		givenAScenario(consumer)
				.onEvent(() -> new SomeEvent("foo"))
				.expectSuccess();
	}

	@Test // GH-136
	void matchesEventsExtractingPayloadWithPredicate() throws Throwable {

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(SomeEvent.class)
				.matchingMapped(SomeEvent::payload, __ -> __.startsWith("foo"))
				.toArrive();

		givenAScenario(consumer)
				.onEvent(() -> new SomeEvent("foo"))
				.expectSuccess();
	}

	@Test // GH-136
	void matchesEventsExtractingPayloadWithValue() throws Throwable {

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(SomeEvent.class)
				.matchingMappedValue(SomeEvent::payload, "foo")
				.toArrive();

		givenAScenario(consumer)
				.onEvent(() -> new SomeEvent("foo"))
				.expectSuccess();
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersVerificationOnSuccess() {

		Consumer<String> verification = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(String.class)
				.toArriveAndVerify(verification);

		givenAScenario(consumer)
				.onEvent(() -> "foo")
				.expectSuccess();

		verify(verification).accept("foo");
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersVerificationWithResultOnSuccess() {

		BiConsumer<String, Integer> verification = mock(BiConsumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate(() -> 41)
				.forEventOfType(String.class)
				.toArriveAndVerify(verification);

		givenAScenario(consumer)
				.onEvent(() -> "foo")
				.expectSuccess();

		verify(verification).accept("foo", 41);
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void doesNotTriggerVerificationOnFailure() {

		Consumer<String> verification = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(String.class)
				.toArriveAndVerify(verification);

		givenAScenario(consumer)
				.onEvent(() -> 1)
				.expectFailure();

		verify(verification, never()).accept(any());
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void doesNotTriggerVerificationWithResultOnFailure() {

		BiConsumer<String, Void> verification = mock(BiConsumer.class);

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(String.class)
				.toArriveAndVerify(verification);

		givenAScenario(consumer)
				.onEvent(() -> 1)
				.expectFailure();

		verify(verification, never()).accept(any(), any());
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersCleanupOnSuccess() {

		BiConsumer<String, Integer> verification = mock(BiConsumer.class);
		Consumer<Integer> cleanupCallback = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate(() -> 41, cleanupCallback)
				.forEventOfType(String.class)
				.toArriveAndVerify(verification);

		givenAScenario(consumer)
				.onEvent(() -> "foo")
				.expectSuccess();

		verify(verification).accept("foo", 41);
		verify(cleanupCallback).accept(41);
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersCleanupOnFailure() {

		BiConsumer<String, Integer> verification = mock(BiConsumer.class);
		Consumer<Integer> cleanupCallback = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate((tx) -> 41, cleanupCallback)
				.andWaitAtMost(WAIT_TIME)
				.forEventOfType(String.class)
				.toArriveAndVerify(verification);

		givenAScenario(consumer)
				.onEvent(() -> 4711)
				.expectFailure();

		verify(verification, never()).accept(any(), any());
		verify(cleanupCallback).accept(41);
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersAssertOnSuccess() {

		Consumer<PublishedEventAssert<? super String>> verification = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> publishObject(it)
				.forEventOfType(String.class)
				.toArriveAndAssert(verification);

		givenAScenario(consumer)
				.onEvent(() -> "foo")
				.expectSuccess();

		verify(verification).accept(any());
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersAssertWithResultOnSuccess() {

		BiConsumer<PublishedEventAssert<? super String>, Integer> verification = mock(BiConsumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate((tx) -> 41)
				.forEventOfType(String.class)
				.toArriveAndAssert(verification);

		givenAScenario(consumer)
				.onEvent(() -> "foo")
				.expectSuccess();

		verify(verification).accept(any(), eq(41));
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersVerificationOnSuccessfulStateChange() {

		Consumer<String> verification = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate(() -> 41)
				.andWaitForStateChange(delayed("Foo"))
				.andVerify(verification);

		givenAScenario(consumer)
				.expectSuccess();

		verify(verification).accept("Foo");
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void triggersVerificationOnSuccessfulStateChangeWithResult() {

		BiConsumer<String, Integer> verification = mock(BiConsumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate(() -> 41)
				.andWaitForStateChange(delayed("Foo"))
				.andVerify(verification);

		givenAScenario(consumer)
				.expectSuccess();

		verify(verification).accept("Foo", 41);
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void doesNotTriggerVerificationOnFailedStateChange() {

		Consumer<String> verification = mock(Consumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate(() -> 41)
				.andWaitAtMost(WAIT_TIME)
				.forStateChange(delayed("Foo", TIMED_OUT))
				.andVerify(verification);

		givenAScenario(consumer)
				.expectFailure();

		verify(verification, never()).accept(any());
	}

	@Test // GH-136
	@SuppressWarnings("unchecked")
	void doesNotTriggerVerificationWithResultOnFailedStateChange() {

		BiConsumer<String, Integer> verification = mock(BiConsumer.class);

		Consumer<Scenario> consumer = it -> it.stimulate(() -> 41)
				.andWaitAtMost(WAIT_TIME)
				.forStateChange(delayed("Foo", TIMED_OUT))
				.andVerify(verification);

		givenAScenario(consumer)
				.expectFailure();

		verify(verification, never()).accept(any(), any());
	}

	private Fixture givenAScenario(Consumer<Scenario> consumer) {
		return new Fixture(consumer, DELAY, null, new DefaultAssertablePublishedEvents());
	}

	private static When<Void> publishObject(Scenario source) {
		return source.publish(new Object())
				.andWaitAtMost(WAIT_TIME);
	}

	private static <S> Supplier<S> delayed(S value) {
		return delayed(value, DELAY);
	}

	private static <S> Supplier<S> delayed(S value, Duration duration) {

		return () -> {
			try {
				Thread.sleep(duration.toMillis());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			return value;
		};
	}

	@RequiredArgsConstructor
	class Fixture {

		private final Consumer<Scenario> consumer;
		private final Duration duration;
		private final Runnable runnable;
		private final DefaultAssertablePublishedEvents events;

		private CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();

		public Fixture waitFor(Duration duration) {
			return new Fixture(consumer, duration, runnable, events);
		}

		public Fixture onEvent(Supplier<Object> event) {

			Runnable runnable = () -> events.onApplicationEvent(new PayloadApplicationEvent<Object>(this, event.get()));

			return new Fixture(consumer, duration, runnable, events);
		}

		public void expectSuccess() {

			foo();

			exceptionHandler.throwIfCaught();
		}

		public void expectFailure() {

			foo();

			assertThat(exceptionHandler.caught)
					.as("Expected failure but did not see an exception!")
					.isNotNull();
		}

		private void foo() {

			try {

				Runnable foo = () -> consumer.accept(new Scenario(tx, publisher, events));

				var thread = new Thread(foo);
				thread.setUncaughtExceptionHandler(exceptionHandler);
				thread.start();

				Thread.sleep(duration.toMillis());

				if (runnable != null) {
					runnable.run();
				}

				thread.join();

			} catch (Throwable o_O) {

				if (o_O instanceof RuntimeException e) {
					throw e;
				}

				throw new RuntimeException(o_O);
			}
		}
	}

	record SomeEvent(String payload) {}

	enum StubTransactionOperations implements TransactionOperations {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.transaction.support.TransactionOperations#execute(org.springframework.transaction.support.TransactionCallback)
		 */
		@Override
		public <T> T execute(TransactionCallback<T> action) throws TransactionException {
			return action.doInTransaction(new SimpleTransactionStatus());
		}
	}

	static class CapturingExceptionHandler implements UncaughtExceptionHandler {

		Throwable caught;

		/*
		 * (non-Javadoc)
		 * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
		 */
		@Override
		public void uncaughtException(Thread t, Throwable e) {
			caught = e;
		}

		public void throwIfCaught() {

			if (caught == null) {
				return;
			}

			if (caught instanceof RuntimeException e) {
				throw e;
			}

			throw new RuntimeException(caught);
		}
	}
}
