/*
 * Copyright 2017-2026 the original author or authors.
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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.AbstractApplicationEventMulticaster;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/**
 * An {@link org.springframework.context.event.ApplicationEventMulticaster} to register {@link EventPublication}s in an
 * {@link EventPublicationRegistry} so that potentially failing transactional event listeners can get re-invoked upon
 * application restart or via a schedule.
 * <p>
 * Republication is handled in {@link #afterSingletonsInstantiated()} inspecting the {@link EventPublicationRegistry}
 * for incomplete publications.
 *
 * @author Oliver Drotbohm
 * @author Yunho Jung
 * @see CompletionRegisteringAdvisor
 */
public class PersistentApplicationEventMulticaster extends AbstractApplicationEventMulticaster
		implements FailedEventPublications, IncompleteEventPublications, SmartInitializingSingleton {

	private static final Logger LOGGER = LoggerFactory.getLogger(PersistentApplicationEventMulticaster.class);

	static final String REPUBLISH_ON_RESTART = "spring.modulith.events.republish-outstanding-events-on-restart";
	static final String REPUBLISH_ON_RESTART_LEGACY = "spring.modulith.republish-outstanding-events-on-restart";

	private final @NonNull Supplier<EventPublicationRegistry> registry;
	private final @NonNull Supplier<Environment> environment;
	private final @NonNull Supplier<EventExternalizationConfiguration> externalizationConfiguration;

	/**
	 * Creates a new {@link PersistentApplicationEventMulticaster} for the given {@link EventPublicationRegistry}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	public PersistentApplicationEventMulticaster(Supplier<EventPublicationRegistry> registry,
			Supplier<Environment> environment) {
		this(registry, environment, EventExternalizationConfiguration::disabled);
	}

	/**
	 * Creates a new {@link PersistentApplicationEventMulticaster} for the given {@link EventPublicationRegistry}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 * @param externalizationConfiguration must not be {@literal null}.
	 */
	public PersistentApplicationEventMulticaster(Supplier<EventPublicationRegistry> registry,
			Supplier<Environment> environment,
			Supplier<EventExternalizationConfiguration> externalizationConfiguration) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");
		Assert.notNull(environment, "Environment must not be null!");
		Assert.notNull(externalizationConfiguration, "EventExternalizationConfiguration must not be null!");

		this.registry = registry;
		this.environment = environment;
		this.externalizationConfiguration = externalizationConfiguration;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.event.ApplicationEventMulticaster#multicastEvent(org.springframework.context.ApplicationEvent)
	 */
	@Override
	public void multicastEvent(ApplicationEvent event) {
		multicastEvent(event, ResolvableType.forInstance(event));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.event.ApplicationEventMulticaster#multicastEvent(org.springframework.context.ApplicationEvent, org.springframework.core.ResolvableType)
	 */
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType) {

		var type = eventType == null ? ResolvableType.forInstance(event) : eventType;
		var listeners = getApplicationListeners(event, type);

		if (listeners.isEmpty()) {
			return;
		}

		var eventToPersist = getEventToPersist(event);
		var transactionalListeners = new TransactionalEventListeners(listeners);

		// Detect events configured for externalization published outside transaction context
		detectEventPublishedOutsideTransaction(transactionalListeners, eventToPersist);

		transactionalListeners.ifPresent(it -> storePublications(it, eventToPersist));

		for (ApplicationListener listener : listeners) {
			listener.onApplicationEvent(event);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.event.AbstractApplicationEventMulticaster#getApplicationListeners(org.springframework.context.ApplicationEvent, org.springframework.core.ResolvableType)
	 */
	@Override
	protected Collection<ApplicationListener<?>> getApplicationListeners(ApplicationEvent event,
			ResolvableType eventType) {

		Object eventToPersist = getEventToPersist(event);

		return super.getApplicationListeners(event, eventType)
				.stream()
				.filter(it -> matches(event, eventToPersist, it))
				.toList();
	}

	/*
	* (non-Javadoc)
	* @see org.springframework.modulith.events.IncompleteEventPublications#resubmitIncompletePublications(java.util.function.Predicate)
	*/
	@Override
	public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
		doResubmitUncompletedPublicationsOlderThan(null, filter);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.IncompleteEventPublications#resubmitIncompletePublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public void resubmitIncompletePublicationsOlderThan(Duration duration) {
		doResubmitUncompletedPublicationsOlderThan(duration, __ -> true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.IncompleteEventPublications#resubmitIncompletePublications(org.springframework.modulith.events.ResubmissionOptions)
	 */
	@Override
	public void resubmitIncompletePublications(ResubmissionOptions options) {
		doResubmitIncompletePublications(options);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.FailedEventPublications#resubmit(org.springframework.modulith.events.ResubmissionOptions)
	 */
	@Override
	public void resubmit(ResubmissionOptions options) {
		doResubmitIncompletePublications(options);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.SmartInitializingSingleton#afterSingletonsInstantiated()
	 */
	@Override
	public void afterSingletonsInstantiated() {

		var env = environment.get();

		Boolean republishOnRestart = Optional.ofNullable(env.getProperty(REPUBLISH_ON_RESTART, Boolean.class))
				.orElseGet(() -> env.getProperty(REPUBLISH_ON_RESTART_LEGACY, Boolean.class));

		if (!Boolean.TRUE.equals(republishOnRestart)) {
			return;
		}

		resubmitIncompletePublications(__ -> true);
	}

	private void invokeTargetListener(TargetEventPublication publication) {

		var listeners = new TransactionalEventListeners(
				getApplicationListeners());

		listeners.stream() //
				.filter(it -> publication.isIdentifiedBy(PublicationTargetIdentifier.of(it.getListenerId()))) //
				.findFirst() //
				.map(it -> executeListenerWithCompletion(publication, it)) //
				.orElseGet(() -> {

					LOGGER.error("Listener {} not found! Skipping invocation and leaving event publication {} failed.",
							publication.getTargetIdentifier(), publication.getIdentifier());

					registry.get().markFailed(publication.getEvent(), publication.getTargetIdentifier());

					return null;
				});
	}

	private void doResubmitUncompletedPublicationsOlderThan(@Nullable Duration duration,
			Predicate<EventPublication> filter) {

		registry.get().processIncompletePublications(filter, this::invokeTargetListener, duration);
	}

	private void doResubmitIncompletePublications(ResubmissionOptions options) {
		registry.get().processFailedPublications(options, this::invokeTargetListener);
	}

	private static ApplicationListener<ApplicationEvent> executeListenerWithCompletion(EventPublication publication,
			TransactionalApplicationListener<ApplicationEvent> listener) {

		listener.processEvent(publication.getApplicationEvent());

		return listener;
	}

	private void storePublications(Stream<TransactionalApplicationListener<ApplicationEvent>> listeners,
			Object eventToPersist) {

		var identifiers = listeners.map(TransactionalApplicationListener::getListenerId) //
				.map(PublicationTargetIdentifier::of);

		registry.get().store(eventToPersist, identifiers);
	}

	private static Object getEventToPersist(ApplicationEvent event) {

		return PayloadApplicationEvent.class.isInstance(event) //
				? ((PayloadApplicationEvent<?>) event).getPayload() //
				: event;
	}

	private static boolean matches(ApplicationEvent event, Object payload, ApplicationListener<?> listener) {

		// Verify general listener matching by eagerly evaluating the condition
		if (!invokeShouldHandle(listener, event)) {
			return false;
		}

		return ConditionalEventListener.class.isInstance(listener)
				? ConditionalEventListener.class.cast(listener).supports(payload)
				: true;
	}

	/**
	 * Checks if the given listener should handle the specified event by invoking
	 * {@link ApplicationListenerMethodAdapter#shouldHandle(ApplicationEvent)} when applicable.
	 *
	 * @param candidate the listener to test, must not be {@literal null}.
	 * @param event the event to publish, must not be {@literal null}.
	 * @return whether the event should be handled by the given candidate.
	 */
	private static boolean invokeShouldHandle(ApplicationListener<?> candidate, ApplicationEvent event) {

		if (candidate instanceof ApplicationListenerMethodAdapter listener) {
			return listener.shouldHandle(event);
		}

		return true;
	}

	/**
	 * Detects if an event selected for externalization is published outside a transaction context.
	 * If detected, logs a warning message to help developers identify the problem.
	 *
	 * @param transactionalListeners must not be {@literal null}.
	 * @param event must not be {@literal null}.
	 */
	private void detectEventPublishedOutsideTransaction(TransactionalEventListeners transactionalListeners,
			Object event) {

		// Transaction is active, no problem
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			return;
		}

		// No transactional listeners, nothing to check
		if (!transactionalListeners.hasListeners()) {
			return;
		}

		// Check if the event is configured for externalization
		var config = externalizationConfiguration.get();
		if (!config.supports(event)) {
			return;
		}

		// Issue a warning log hinting at the problem
		LOGGER.warn(
				"Event of type {} is configured for externalization but was published outside an active transaction. "
						+ "It will neither be persisted nor externalized. Consider publishing from a @Transactional method.",
				event.getClass().getName());
	}

	/**
	 * First-class collection to work with transactional event listeners, i.e. {@link ApplicationListener} instances that
	 * implement {@link TransactionalApplicationListener}.
	 *
	 * @author Oliver Drotbohm
	 * @see org.springframework.transaction.event.TransactionalEventListener
	 * @see TransactionalApplicationListener
	 */
	static class TransactionalEventListeners {

		private final List<TransactionalApplicationListener<ApplicationEvent>> listeners;

		/**
		 * Creates a new {@link TransactionalEventListeners} instance by filtering all elements implementing
		 * {@link TransactionalApplicationListener}.
		 *
		 * @param listeners must not be {@literal null}.
		 */
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public TransactionalEventListeners(Collection<ApplicationListener<?>> listeners) {

			Assert.notNull(listeners, "ApplicationListeners must not be null!");

			this.listeners = (List) listeners.stream()
					.filter(TransactionalApplicationListener.class::isInstance)
					.map(TransactionalApplicationListener.class::cast)
					.filter(it -> it.getTransactionPhase().equals(TransactionPhase.AFTER_COMMIT))
					.sorted(AnnotationAwareOrderComparator.INSTANCE)
					.toList();
		}

		/**
		 * Invokes the given {@link Consumer} for all transactional event listeners.
		 *
		 * @param callback must not be {@literal null}.
		 */
		public void forEach(Consumer<TransactionalApplicationListener<?>> callback) {

			Assert.notNull(callback, "Callback must not be null!");

			listeners.forEach(callback);
		}

		/**
		 * Executes the given consumer only if there are actual listeners available.
		 *
		 * @param metadata must not be {@literal null}.
		 */
		public void ifPresent(Consumer<Stream<TransactionalApplicationListener<ApplicationEvent>>> metadata) {

			Assert.notNull(metadata, "Callback must not be null!");

			if (!listeners.isEmpty()) {
				metadata.accept(listeners.stream());
			}
		}

		/**
		 * Returns all transactional event listeners.
		 *
		 * @return will never be {@literal null}.
		 */
		public Stream<TransactionalApplicationListener<ApplicationEvent>> stream() {
			return listeners.stream();
		}

		/**
		 * Invokes the given {@link Consumer} for the listener with the given identifier.
		 *
		 * @param identifier must not be {@literal null} or empty.
		 * @param callback must not be {@literal null}.
		 */
		public void doWithListener(String identifier,
				Consumer<TransactionalApplicationListener<ApplicationEvent>> callback) {

			Assert.hasText(identifier, "Identifier must not be null or empty!");
			Assert.notNull(callback, "Callback must not be null!");

			listeners.stream()
					.filter(it -> it.getListenerId().equals(identifier))
					.findFirst()
					.ifPresent(callback);
		}

		/**
		 * Returns whether there are any transactional event listeners.
		 *
		 * @return whether listeners exist.
		 */
		public boolean hasListeners() {
			return !listeners.isEmpty();
		}
	}
}
