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
package org.springframework.modulith.events.namastack;

import io.namastack.outbox.handler.OutboxHandler;
import io.namastack.outbox.handler.OutboxRecordMetadata;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.util.Assert;

/**
 * An {@link OutboxHandler} that transports events from the outbox to an external target.
 * <p>
 * This handler is invoked by the outbox processor to externalize events that were previously recorded in the outbox
 * table. It uses the configured transport function to deliver events to their target destination (e.g., message broker,
 * email, SMS).
 *
 * @author Roland Beisel
 * @since 2.1
 * @see OutboxHandler
 * @see EventExternalizationConfiguration
 */
public class NamastackOutboxEventTransport implements OutboxHandler {

	private final EventExternalizationConfiguration configuration;
	private final BiFunction<RoutingTarget, Object, CompletableFuture<?>> transport;

	/**
	 * Creates a new {@link OutboxEventTransport} for the given {@link EventExternalizationConfiguration} and transport
	 * function.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param transport must not be {@literal null}.
	 */
	public NamastackOutboxEventTransport(EventExternalizationConfiguration configuration,
			BiFunction<RoutingTarget, Object, CompletableFuture<?>> transport) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");
		Assert.notNull(transport, "Transport function must not be null!");

		this.configuration = configuration;
		this.transport = transport;
	}

	/**
	 * Handles an outbox event by determining its routing target and transporting it to the external target using the
	 * configured transport function.
	 *
	 * @param event the event payload to transport
	 * @param metadata the outbox record metadata
	 */
	@Override
	public void handle(Object event, OutboxRecordMetadata metadata) {

		var target = configuration.determineTarget(event);

		try {
			transport.apply(target, event).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException("Failed to transport event to " + target.getTarget(), e);
		}
	}
}
