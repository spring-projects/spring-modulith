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

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TransactionalEventListeners;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * An {@link org.springframework.context.event.ApplicationEventMulticaster} to register
 * {@link org.springframework.modulith.events.EventPublication}s in an {@link EventPublicationRegistry} so that
 * potentially failing transactional event listeners can get re-invoked upon application restart or via a schedule.
 *
 * @author Oliver Drotbohm
 * @author Seonwoo Jung
 * @see CompletionRegisteringAdvisor
 * @see org.springframework.modulith.events.core.DefaultFailedEventPublications
 */
public class PersistentApplicationEventMulticaster extends SimpleApplicationEventMulticaster {

	private final Map<CacheKey, TransactionalEventListeners> cache = new ConcurrentReferenceHashMap<>();
	private final Supplier<EventPublicationRegistry> registry;
	private final Supplier<Environment> environment;

	/**
	 * Creates a new {@link PersistentApplicationEventMulticaster} for the given {@link EventPublicationRegistry}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	public PersistentApplicationEventMulticaster(Supplier<EventPublicationRegistry> registry,
			Supplier<Environment> environment) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");
		Assert.notNull(environment, "Environment must not be null!");

		this.registry = registry;
		this.environment = environment;
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
	@SuppressWarnings({ "rawtypes" })
	public void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType) {

		var type = eventType == null ? ResolvableType.forInstance(event) : eventType;
		var candidates = super.getApplicationListeners(event, type);

		if (candidates.isEmpty()) {
			return;
		}

		var eventToPersist = getEventToPersist(event);

		// Find all listeners that will need to be invoked
		var matchingListeners = candidates.stream() //
				.filter(it -> matches(event, eventToPersist, it)) //
				.toList();

		if (matchingListeners.isEmpty()) {
			return;
		}

		// From the candidates find the transactional ones and cache them by source and target type
		cache.computeIfAbsent(new CacheKey(type, getSourceType(event)),
				__ -> new TransactionalEventListeners(candidates, environment))

				// Make sure we honor the by-event instance evaluated conditions
				.filter(matchingListeners::contains)
				.ifPresent(stream -> storePublications(stream, eventToPersist));

		for (ApplicationListener listener : matchingListeners) {
			invokeListener(listener, event);
		}
	}

	/**
	 * Returns the {@link TransactionalEventListeners} currently registered with this multicaster.
	 *
	 * @return will never be {@literal null}.
	 * @since 2.2
	 */
	public TransactionalEventListeners getTransactionalEventListeners() {
		return new TransactionalEventListeners(getApplicationListeners(), environment);
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

	private static @Nullable Class<?> getSourceType(ApplicationEvent event) {

		var source = event.getSource();

		return source != null ? source.getClass() : null;
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

		return candidate instanceof ApplicationListenerMethodAdapter listener
				? listener.shouldHandle(event)
				: true;
	}

	private record CacheKey(ResolvableType eventType, @Nullable Class<?> sourceType) {

		private CacheKey {
			Assert.notNull(eventType, "Event type must not be null");
		}
	}
}
