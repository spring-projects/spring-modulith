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
package org.springframework.modulith.events.support;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.AbstractApplicationEventMulticaster;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.NonNull;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRegistry;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.Assert;

/**
 * An {@link ApplicationEventMulticaster} to register {@link EventPublication}s in an {@link EventPublicationRegistry}
 * so that potentially failing transactional event listeners can get re-invoked upon application restart or via a
 * schedule.
 * <p>
 * Republication is handled in {@link #afterSingletonsInstantiated()} inspecting the {@link EventPublicationRegistry}
 * for incomplete publications and
 *
 * @author Oliver Drotbohm
 * @see CompletionRegisteringBeanPostProcessor
 */
public class PersistentApplicationEventMulticaster extends AbstractApplicationEventMulticaster
		implements SmartInitializingSingleton {

	private static final Logger LOGGER = LoggerFactory.getLogger(PersistentApplicationEventMulticaster.class);

	private final @NonNull Supplier<EventPublicationRegistry> registry;

	/**
	 * Creates a new {@link PersistentApplicationEventMulticaster} for the given {@link EventPublicationRegistry}.
	 *
	 * @param registry must not be {@literal null}.
	 */
	public PersistentApplicationEventMulticaster(Supplier<EventPublicationRegistry> registry) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");

		this.registry = registry;
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
	public void multicastEvent(ApplicationEvent event, ResolvableType eventType) {

		var type = eventType == null ? ResolvableType.forInstance(event) : eventType;
		var listeners = getApplicationListeners(event, type);

		if (listeners.isEmpty()) {
			return;
		}

		var txListeners = new TransactionalEventListeners(listeners);
		var eventToPersist = getEventToPersist(event);

		registry.get().store(eventToPersist, txListeners.stream() //
				.map(TransactionalApplicationListener::getListenerId) //
				.map(PublicationTargetIdentifier::of));

		for (ApplicationListener listener : listeners) {
			listener.onApplicationEvent(event);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.SmartInitializingSingleton#afterSingletonsInstantiated()
	 */
	@Override
	public void afterSingletonsInstantiated() {

		for (EventPublication publication : registry.get().findIncompletePublications()) {
			invokeTargetListener(publication);
		}
	}

	private void invokeTargetListener(EventPublication publication) {

		var listeners = new TransactionalEventListeners(
				getApplicationListeners());

		listeners.stream() //
				.filter(it -> publication.isIdentifiedBy(PublicationTargetIdentifier.of(it.getListenerId()))) //
				.findFirst() //
				.map(it -> executeListenerWithCompletion(publication, it)) //
				.orElseGet(() -> {

					LOGGER.debug("Listener {} not found!", publication.getTargetIdentifier());
					return null;
				});
	}

	private ApplicationListener<ApplicationEvent> executeListenerWithCompletion(EventPublication publication,
			TransactionalApplicationListener<ApplicationEvent> listener) {

		listener.processEvent(publication.getApplicationEvent());

		return listener;
	}

	private static Object getEventToPersist(ApplicationEvent event) {

		return PayloadApplicationEvent.class.isInstance(event) //
				? ((PayloadApplicationEvent<?>) event).getPayload() //
				: event;
	}

	/**
	 * First-class collection to work with transactional event listeners, i.e. {@link ApplicationListener} instances that
	 * implement {@link TransactionalApplicationListener}.
	 *
	 * @author Oliver Drotbohm
	 * @see TransactionalEventListener
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
					.sorted(AnnotationAwareOrderComparator.INSTANCE)
					.toList();
		}

		private TransactionalEventListeners(
				List<TransactionalApplicationListener<ApplicationEvent>> listeners) {
			this.listeners = listeners;
		}

		/**
		 * Returns all {@link TransactionalEventListeners} for the given {@link TransactionPhase}.
		 *
		 * @param phase must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public TransactionalEventListeners forPhase(TransactionPhase phase) {

			Assert.notNull(phase, "TransactionPhase must not be null!");

			List<TransactionalApplicationListener<ApplicationEvent>> collect = listeners.stream()
					.filter(it -> it.getTransactionPhase().equals(phase))
					.toList();

			return new TransactionalEventListeners(collect);
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
	}
}
