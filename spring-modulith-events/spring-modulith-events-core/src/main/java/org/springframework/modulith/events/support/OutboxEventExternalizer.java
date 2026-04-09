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
	 */
	public OutboxEventExternalizer(EventExternalizationConfiguration configuration, ApplicationEventPublisher publisher,
			EventExternalizationTransport transport) {

		super(configuration, transport);

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
}
