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

import io.namastack.outbox.Outbox;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.util.Assert;

/**
 * An {@link ApplicationListener} that listens to all {@link PayloadApplicationEvent}s and schedules externalized events
 * to an {@link Outbox} for later processing. This listener operates synchronously within the same transaction as the
 * event publisher, ensuring the outbox record is committed or rolled back together with the business operation.
 *
 * @author Roland Beisel
 * @since 2.1
 * @see Outbox
 * @see EventExternalizationConfiguration
 */
class NamastackOutboxEventRecorder implements ApplicationListener<PayloadApplicationEvent<?>> {

	private static final Logger LOGGER = LoggerFactory.getLogger(NamastackOutboxEventRecorder.class);

	private final EventExternalizationConfiguration configuration;
	private final Outbox outbox;

	/**
	 * Creates a new {@link OutboxEventRecorder} for the given {@link EventExternalizationConfiguration} and
	 * {@link Outbox}.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param outbox must not be {@literal null}.
	 */
	NamastackOutboxEventRecorder(EventExternalizationConfiguration configuration, Outbox outbox) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");
		Assert.notNull(outbox, "Outbox must not be null!");

		this.configuration = configuration;
		this.outbox = outbox;
	}

	/**
	 * Handles incoming application events by checking if they should be externalized and scheduling them to the outbox
	 * within the current transaction.
	 *
	 * @param event the payload application event to process
	 */
	@Override
	public void onApplicationEvent(PayloadApplicationEvent<?> event) {

		var payload = event.getPayload();

		if (!configuration.supports(payload)) {
			return;
		}

		var target = configuration.determineTarget(payload);
		var mapped = configuration.map(payload);
		var key = target.getKey();

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Scheduling event of type {} to outbox for target {}.",
					payload.getClass().getName(), target.getTarget());
		}

		scheduleToOutbox(mapped, key);
	}

	/**
	 * Schedules the given payload to the outbox, optionally with a routing key.
	 *
	 * @param payload must not be {@literal null}.
	 * @param key can be {@literal null}.
	 */
	private void scheduleToOutbox(Object payload, @Nullable String key) {

		if (key != null) {
			outbox.schedule(payload, key);
		} else {
			outbox.schedule(payload);
		}
	}
}
