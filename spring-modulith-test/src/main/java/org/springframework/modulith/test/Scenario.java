/*
 * Copyright 2023-2025 the original author or authors.
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.modulith.test.PublishedEvents.TypedPublishedEvents;
import org.springframework.modulith.test.PublishedEventsAssert.PublishedEventAssert;
import org.springframework.modulith.test.Scenario.When.EventResult;
import org.springframework.modulith.test.Scenario.When.StateChangeResult;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * A DSL to define integration testing scenarios for application modules. A {@link Scenario} starts with a stimulus on
 * the system, usually a component invocation (see {@link #stimulate(Function)} or event publication (see
 * {@link #publish(Object...)}) and a definition of the expected outcome. That can be a state change observed by
 * invoking application module components (see {@link When#andWaitForStateChange(Supplier)}) or another event being
 * published (see {@link When#andWaitForEventOfType(Class)}), concluded by additional verifications.
 * <p>
 * {@link Scenario} can be used as JUnit test method parameter in {@link ApplicationModuleTest}s.
 *
 * @author Oliver Drotbohm
 * @see ApplicationModuleTest
 */
public class Scenario {

	private static final Predicate<Object> DEFAULT_ACCEPTANCE = it -> {

		if (it instanceof Optional<?> o) {
			return o.isPresent();
		}

		if (it instanceof Boolean b) {
			return b;
		}

		return it != null;
	};

	private final TransactionOperations transactionOperations;
	private final ApplicationEventPublisher publisher;
	private final AssertablePublishedEvents events;

	private Function<ConditionFactory, ConditionFactory> defaultCustomizer;

	/**
	 * Creates a new {@link Scenario} for the given {@link TransactionTemplate}, {@link ApplicationEventPublisher} and
	 * {@link AssertablePublishedEvents}.
	 *
	 * @param transactionTemplate must not be {@literal null}.
	 * @param publisher must not be {@literal null}.
	 * @param events must not be {@literal null}.
	 */
	Scenario(TransactionTemplate transactionTemplate, ApplicationEventPublisher publisher,
			AssertablePublishedEvents events) {

		Assert.notNull(transactionTemplate, "TransactionTemplate must not be null!");
		Assert.notNull(publisher, "ApplicationEventPublisher must not be null!");
		Assert.notNull(events, "AssertablePublishedEvents must not be null!");

		var definition = new DefaultTransactionDefinition(transactionTemplate);
		definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		this.transactionOperations = new TransactionTemplate(transactionTemplate.getTransactionManager(), definition);
		this.publisher = publisher;
		this.events = events;
		this.defaultCustomizer = Function.identity();
	}

	/**
	 * Publishes the given event. The event will be published in a transaction to make sure that transactional event
	 * listeners are invoked as well.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public When<Void> publish(Object event) {
		return stimulate((tx, e) -> {
			tx.executeWithoutResult(__ -> e.publishEvent(event));
		});
	}

	/**
	 * Publishes the event provided by the given {@link Supplier}. The event will be published in a transaction to make
	 * sure that transactional event listeners are invoked as well.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public When<Void> publish(Supplier<Object> event) {

		return stimulate((tx, e) -> {
			tx.executeWithoutResult(__ -> e.publishEvent(event.get()));
		});
	}

	/**
	 * Stimulates the system by executing the given {@link Runnable}.
	 *
	 * @param runnable must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public When<Void> stimulate(Runnable runnable) {

		Assert.notNull(runnable, "Runnable must not be null!");

		return stimulate(() -> {
			runnable.run();
			return null;
		});
	}

	/**
	 * Stimulates the system using the given {@link Supplier} and keeping the supplied value around for later
	 * verification.
	 *
	 * @param <S> the type of the value returned by the stimulus.
	 * @param supplier must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @see StateChangeResult#andVerify(Consumer)
	 * @see EventResult#toArriveAndVerify(Consumer)
	 */
	public <S> When<S> stimulate(Supplier<S> supplier) {
		return stimulate(tx -> tx.execute(__ -> supplier.get()));
	}

	/**
	 * Stimulates the system using the given function providing access to the {@link TransactionOperations} and keeping
	 * the supplied value around for later verification.
	 *
	 * @param <S> the type of the value returned by the stimulus.
	 * @param function must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public <S> When<S> stimulate(Function<TransactionOperations, S> function) {
		return stimulate((tx, __) -> {
			return function.apply(tx);
		});
	}

	/**
	 * Stimulate the system using the given {@link TransactionOperations} and {@link ApplicationEventPublisher}. Usually a
	 * method on some application service or event publication is triggered.
	 *
	 * @param stimulus must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public When<Void> stimulate(BiConsumer<TransactionOperations, ApplicationEventPublisher> stimulus) {

		Assert.notNull(stimulus, "Stimulus must not be null!");

		return stimulate((tx, e) -> {
			stimulus.accept(tx, e);
			return (Void) null;
		});
	}

	/**
	 * Stimulate the system using the given {@link TransactionOperations} and {@link ApplicationEventPublisher} and
	 * produce a result. Usually a method on some application service or event publication is triggered.
	 *
	 * @param <S> the type of the result.
	 * @param stimulus must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public <S> When<S> stimulate(BiFunction<TransactionOperations, ApplicationEventPublisher, S> stimulus) {

		Assert.notNull(stimulus, "Stimulus must not be null!");

		return new When<>(stimulus, __ -> {}, defaultCustomizer);
	}

	/**
	 * Extension hook to allow registration of a global customizer. If none configured we will fall back to
	 * {@link Function#identity()}.
	 *
	 * @param customizer must not be {@literal null}.
	 * @see org.springframework.modulith.test.ScenarioCustomizer
	 */
	Scenario setDefaultCustomizer(Function<ConditionFactory, ConditionFactory> customizer) {

		Assert.notNull(customizer, "Customizer must not be null!");

		this.defaultCustomizer = customizer;
		return this;
	}

	public class When<T> {

		private final BiFunction<TransactionOperations, ApplicationEventPublisher, T> stimulus;
		private final Consumer<T> cleanup;
		private final Function<ConditionFactory, ConditionFactory> customizer;

		/**
		 * @param stimulus must not be {@literal null}.
		 * @param cleanup must not be {@literal null}.
		 * @param customizer must not be {@literal null}.
		 */
		When(BiFunction<TransactionOperations, ApplicationEventPublisher, T> stimulus, Consumer<T> cleanup,
				Function<ConditionFactory, ConditionFactory> customizer) {
			this.stimulus = stimulus;
			this.cleanup = cleanup;
			this.customizer = customizer;
		}

		/**
		 * Registers the given {@link Runnable} as cleanup callback to always run after completion of the {@link Scenario},
		 * no matter the outcome of its execution (error or success).
		 *
		 * @param runnable must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public When<T> andCleanup(Runnable runnable) {

			Assert.notNull(runnable, "Cleanup callback must not be null!");

			return andCleanup(__ -> runnable.run());
		}

		/**
		 * Registers the given {@link Consumer} as cleanup callback to always run after completion of the {@link Scenario},
		 * no matter the outcome of its execution (error or success).
		 *
		 * @param consumer must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public When<T> andCleanup(Consumer<T> consumer) {

			Assert.notNull(consumer, "Cleanup callback must not be null!");

			return new When<>(stimulus, consumer, customizer);
		}

		// Customize

		/**
		 * Configures the {@link Scenario} to wait for at most the given duration for an event of the subsequent
		 * specification to arrive.
		 *
		 * @param duration must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public When<T> andWaitAtMost(Duration duration) {

			Assert.notNull(duration, "Duration must not be null!");

			return customize(it -> it.atMost(duration));
		}

		/**
		 * Customize the execution of the scenario. The given customizer will be added to the default one registered via a
		 * {@link org.springframework.modulith.test.ScenarioCustomizer}. In other words, multiple invocations will replace
		 * registrations made in previous calls but always be chained after the default customizations registered.
		 *
		 * @param customizer must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public When<T> customize(Function<ConditionFactory, ConditionFactory> customizer) {

			Assert.notNull(customizer, "Customizer must not be null!");

			return new When<T>(stimulus, cleanup, defaultCustomizer.andThen(customizer));
		}

		// Expect event

		/**
		 * Alternative to {@link #andWaitForEventOfType(Class)} for better readability if execution customizations have been
		 * applied before.
		 *
		 * @param <E> the type of the event.
		 * @param type must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #andWaitForEventOfType(Class)
		 */
		public <E> EventResult<E> forEventOfType(Class<E> type) {
			return andWaitForEventOfType(type);
		}

		/**
		 * Alternative to {@link #andWaitForStateChange(Supplier)} for better readability if execution customizations have
		 * been applied before.
		 *
		 * @param <S> the type of the state change result
		 * @param supplier must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #andWaitForStateChange(Supplier)
		 */
		public <S> StateChangeResult<S> forStateChange(Supplier<S> supplier) {
			return forStateChange(supplier, DEFAULT_ACCEPTANCE);
		}

		/**
		 * Alternative to {@link #andWaitForStateChange(Supplier, Predicate)} for better readability if execution
		 * customizations have been applied before.
		 *
		 * @param <S> the type of the state change result
		 * @param supplier must not be {@literal null}.
		 * @param acceptanceCriteria must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #andWaitForStateChange(Supplier, Predicate)
		 */
		public <S> StateChangeResult<S> forStateChange(Supplier<S> supplier, Predicate<? super S> acceptanceCriteria) {
			return andWaitForStateChange(supplier, acceptanceCriteria);
		}

		/**
		 * Expects an event of the given type to arrive. Use API on the returned {@link EventResult} to specify more
		 * detailed expectations and conclude those with a call a flavor of {@link EventResult#toArrive()}.
		 *
		 * @param <E> the type of the event.
		 * @param type must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #forEventOfType(Class)
		 */
		public <E> EventResult<E> andWaitForEventOfType(Class<E> type) {
			return new EventResult<E>(type, Function.identity(), null);
		}

		/**
		 * Expects a particular state change on the module to produce a result. By default, a non-{@literal null} value
		 * would indicate success, except for {@link java.util.Optional}s, in which case we'd check for the presence of a
		 * value and {@code booleans}, for which we accept {@literal true} as conclusive signal. For more control about the
		 * result matching, use {@link #andWaitForStateChange(Supplier, Predicate)}.
		 *
		 * @param <S> the type of the result.
		 * @param supplier must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #forStateChange(Supplier)
		 */
		public <S> StateChangeResult<S> andWaitForStateChange(Supplier<S> supplier) {
			return andWaitForStateChange(supplier, DEFAULT_ACCEPTANCE);
		}

		/**
		 * Expects a particular state change on the module to produce a result and uses the given {@link Predicate} to
		 * determine whether the value is conclusive.
		 *
		 * @param <S> the type of the result for the state change
		 * @param supplier must not be {@literal null}.
		 * @param acceptanceCriteria must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #andWaitForStateChange(Supplier, Predicate)
		 */
		public <S> StateChangeResult<S> andWaitForStateChange(Supplier<S> supplier,
				Predicate<? super S> acceptanceCriteria) {

			Assert.notNull(supplier, "Supplier must not be null!");
			Assert.notNull(acceptanceCriteria, "Acceptance criteria must not be null!");

			return new StateChangeResult<>(awaitInternal(__ -> {}, () -> supplier.get(), acceptanceCriteria));
		}

		private <S> ExecutionResult<S, T> awaitInternal(Consumer<T> verifications, Callable<S> supplier,
				Predicate<? super S> condition) {

			T result = stimulus.apply(transactionOperations, publisher);

			try {

				S foo = customizer.apply(Awaitility.await())
						.until(supplier, condition);

				verifications.accept(result);

				return new ExecutionResult<>(foo, result);

			} finally {
				cleanup.accept(result);
			}
		}

		private record ExecutionResult<S, T>(S first, T second) {}

		/**
		 * The result of an expected state change.
		 *
		 * @author Oliver Drotbohm
		 */
		public class StateChangeResult<S> {

			private ExecutionResult<S, T> result;

			StateChangeResult(ExecutionResult<S, T> result) {
				this.result = result;
			}

			/**
			 * Verifies the state change result using the given {@link Consumer}.
			 *
			 * @param consumer must not be {@literal null}.
			 */
			public void andVerify(Consumer<S> consumer) {

				Assert.notNull(consumer, "Consumer must not be null!");

				consumer.accept(result.first());
			}

			/**
			 * Verifies the state change result and stimulus result using the given {@link BiConsumer}.
			 *
			 * @param consumer must not be {@literal null}.
			 */
			public void andVerify(BiConsumer<S, T> consumer) {

				Assert.notNull(consumer, "BiConsumer must not be null!");

				consumer.accept(result.first(), result.second());
			}

			/**
			 * Verifies the underlying {@link AssertablePublishedEvents}.
			 *
			 * @param events must not be {@literal null}.
			 */
			public void andVerifyEvents(Consumer<AssertablePublishedEvents> events) {

				Assert.notNull(events, "Consumer must not be null!");

				events.accept(Scenario.this.events);
			}

			/**
			 * Expects an event of the given type to arrive eventually. Use API on the returned {@link EventResult} to specify
			 * more detailed expectations and conclude those with a call a flavor of {@link EventResult#toArrive()}.
			 *
			 * @param <E> the type of the event
			 * @param eventType must not be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public <E> EventResult<E> andExpect(Class<E> eventType) {
				return new EventResult<>(eventType, Function.identity(), result);
			}
		}

		/**
		 * The result of an expected event publication.
		 *
		 * @author Oliver Drotbohm
		 */
		public class EventResult<E> {

			private static final String EXPECTED_EVENT = "Expected an event of type %s (potentially further constrained using matching clauses above) to be published but couldn't find one in %s!";

			private final Class<E> type;
			private final Function<TypedPublishedEvents<E>, TypedPublishedEvents<E>> filter;
			private final ExecutionResult<?, T> previousResult;

			/**
			 * Creates a new {@link EventResult} for the given type and filter.
			 *
			 * @param type must not be {@literal null}.
			 * @param filtered must not be {@literal null}.
			 * @param previousResult a potentially previously calculated result.
			 */
			EventResult(Class<E> type, Function<TypedPublishedEvents<E>, TypedPublishedEvents<E>> filtered,
					ExecutionResult<?, T> previousResult) {

				Assert.notNull(type, "Event type must not be null!");

				this.type = type;
				this.filter = filtered;
				this.previousResult = previousResult;
			}

			/**
			 * Matches events that satisfy the given {@link Predicate}.
			 *
			 * @param filter must not be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public EventResult<E> matching(Predicate<? super E> filter) {

				Assert.notNull(filter, "Filter must not be null!");

				return new EventResult<E>(type, createOrAdd(it -> it.matching(filter)), previousResult);
			}

			/**
			 * Matches events that satisfy the given {@link Predicate} after extracting a value using the given
			 * {@link Function}.
			 *
			 * @param <S> the type of the extracted value.
			 * @param extractor must not be {@literal null}.
			 * @param filter must not be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public <S> EventResult<E> matchingMapped(Function<E, S> extractor, Predicate<? super S> filter) {
				return new EventResult<E>(type, createOrAdd(it -> it.matching(extractor, filter)), previousResult);
			}

			/**
			 * Matches events that extracting the given value using the given {@link Function}.
			 *
			 * @param <S> the type of the extracted value.
			 * @param extractor must not be {@literal null}.
			 * @param value can be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public <S> EventResult<E> matchingMappedValue(Function<E, S> extractor, @Nullable S value) {
				return new EventResult<E>(type, createOrAdd(it -> it.matching(extractor, value)), previousResult);
			}

			/**
			 * Awaits an event of the given specification to arrive.
			 */
			public void toArrive() {
				toArriveAndVerifyInternal(__ -> {});
			}

			/**
			 * Awaits an event of the given specification to arrive and invokes the given consumer with it.
			 *
			 * @param consumer must not be {@literal null}.
			 */
			public void toArriveAndVerify(Consumer<E> consumer) {

				Assert.notNull(consumer, "Consumer must not be null!");

				toArriveAndVerifyInternal(__ -> {
					consumer.accept(getFilteredEvents().iterator().next());
				});
			}

			/**
			 * Awaits an event of the given specification to arrive and invokes the given consumer with it as well as the
			 * result created by the stimulus.
			 *
			 * @param consumer must not be {@literal null}.
			 */
			public void toArriveAndVerify(BiConsumer<E, T> consumer) {

				Assert.notNull(consumer, "Consumer must not be null!");

				toArriveAndVerifyInternal(it -> {
					consumer.accept(getFilteredEvents().iterator().next(), it);
				});
			}

			/**
			 * Expects the events previously specified to arrive and additionally assert the {@link PublishedEventsAssert} for
			 * the captured event.
			 *
			 * @param consumer must not be {@literal null}.
			 */
			public void toArriveAndAssert(Consumer<PublishedEventAssert<? super E>> consumer) {

				Assert.notNull(consumer, "Consumer must not be null!");

				toArriveAndVerifyInternal(__ -> {
					consumer.accept(getAssertedEvent());
				});
			}

			/**
			 * Expects the events previously specified to arrive and additionally assert the {@link PublishedEventsAssert} for
			 * the captured event and original stimulus result.
			 *
			 * @param consumer must not be {@literal null}.
			 */
			public void toArriveAndAssert(BiConsumer<PublishedEventAssert<? super E>, T> consumer) {

				Assert.notNull(consumer, "Consumer must not be null!");

				toArriveAndVerifyInternal(it -> {
					consumer.accept(getAssertedEvent(), it);
				});
			}

			private Function<TypedPublishedEvents<E>, TypedPublishedEvents<E>> createOrAdd(
					Function<TypedPublishedEvents<E>, TypedPublishedEvents<E>> filter) {
				return this.filter == null ? filter : this.filter.andThen(filter);
			}

			private TypedPublishedEvents<E> getFilteredEvents() {
				return filter.apply(events.ofType(type));
			}

			private PublishedEventAssert<? super E> getAssertedEvent() {
				return new PublishedEventsAssert(getFilteredEvents()).contains(type);
			}

			private void toArriveAndVerifyInternal(Consumer<T> verifications) {

				if (previousResult != null) {

					assertThat(getFilteredEvents().eventOfTypeWasPublished(type))
							.overridingErrorMessage(EXPECTED_EVENT, type, events)
							.isTrue();

					verifications.accept(previousResult.second());

				} else {
					awaitInternal(verifications, () -> getFilteredEvents(), it -> it.eventOfTypeWasPublished(type));
				}
			}
		}
	}
}
