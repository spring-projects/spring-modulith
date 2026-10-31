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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * An {@link OutboxEventExternalizer} that transports events from the outbox to an external target.
 * <p>
 * This handler is invoked by the outbox processor to externalize events that were previously recorded in the outbox
 * table. It uses the configured transport function to deliver events to their target destination (e.g., message broker,
 * email, SMS).
 *
 * @author Roland Beisel
 * @author Oliver Drotbohm
 * @since 2.1
 * @see EventExternalizationConfiguration
 */
public class OutboxEventExternalizer extends TransportAwareEventExternalizer {

	private final ApplicationEventPublisher events;

	/**
	 * Creates a new {@link OutboxEventExternalizer} for the given {@link EventExternalizationConfiguration} and transport
	 * function.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param publisher must not be {@literal null}.
	 * @deprecated since 2.2, 2.1.2, for removal in 2.3. Use
	 *             {@link #OutboxEventExternalizer(EventExternalizationConfiguration, ApplicationEventPublisher, EventExternalizationTransport, BeanFactory)}
	 *             instead.
	 */
	@Deprecated(since = "2.2, 2.1.2", forRemoval = true)
	public OutboxEventExternalizer(EventExternalizationConfiguration configuration, ApplicationEventPublisher publisher,
			EventExternalizationTransport transport) {

		super(configuration, transport);

		this.events = publisher;
	}

	/**
	 * Creates a new {@link OutboxEventExternalizer} for the given {@link EventExternalizationConfiguration}, transport
	 * function and {@link BeanFactory} to resolve routing target and key expressions (which may refer to beans) against
	 * the original event.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param publisher must not be {@literal null}.
	 * @param beanFactory must not be {@literal null}.
	 * @since 2.2, 2.1.2
	 */
	public OutboxEventExternalizer(EventExternalizationConfiguration configuration, ApplicationEventPublisher publisher,
			EventExternalizationTransport transport, BeanFactory beanFactory) {

		super(configuration, transport, beanFactory);

		this.events = publisher;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.support.EventExternalizerSupport#externalize(java.lang.Object)
	 */
	@Override
	public CompletableFuture<?> externalize(Object event) {

		var result = super.externalize(event);

		return result.thenApply(it -> {
			events.publishEvent(it);
			return it;
		});
	}

	/**
	 * Externalizes the given event in a blocking way.
	 *
	 * @param event must not be {@literal null}.
	 * @see #externalize(Object)
	 */
	public void externalizeBlocking(Object event) {

		try {

			externalize(event).get();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e.getCause());
		}
	}
}
