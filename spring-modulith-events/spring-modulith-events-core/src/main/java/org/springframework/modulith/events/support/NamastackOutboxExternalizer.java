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

import io.namastack.outbox.handler.OutboxHandler;
import io.namastack.outbox.handler.OutboxHandlerIdentity;
import io.namastack.outbox.handler.OutboxRecordMetadata;

import org.springframework.util.Assert;

/**
 * A Namastack {@link OutboxHandler} handing events selected for externalization to an {@link OutboxEventExternalizer}.
 * Payloads not selected for externalization are not supported, so that Namastack doesn't schedule outbox records for
 * them with this handler.
 *
 * @since 2.1.3
 */
public class NamastackOutboxExternalizer implements OutboxHandler {

	private final OutboxEventExternalizer externalizer;
	private final OutboxHandlerIdentity identity;

	/**
	 * Creates a new {@link NamastackOutboxExternalizer} for the given {@link OutboxEventExternalizer} and handler
	 * identifier.
	 *
	 * @param externalizer must not be {@literal null}.
	 * @param id the identifier of the handler stored with the outbox records, must not be {@literal null} or empty.
	 */
	public NamastackOutboxExternalizer(OutboxEventExternalizer externalizer, String id) {

		Assert.notNull(externalizer, "OutboxEventExternalizer must not be null!");
		Assert.hasText(id, "Handler identifier must not be null or empty!");

		this.externalizer = externalizer;
		this.identity = new OutboxHandlerIdentity(id);
	}

	@Override
	public OutboxHandlerIdentity getGenericHandlerIdentity() {
		return identity;
	}

	@Override
	public boolean supports(Object payload, OutboxRecordMetadata metadata) {
		return externalizer.supports(payload);
	}

	@Override
	public void handle(Object payload, OutboxRecordMetadata metadata) {
		externalizer.externalizeBlocking(payload);
	}
}
