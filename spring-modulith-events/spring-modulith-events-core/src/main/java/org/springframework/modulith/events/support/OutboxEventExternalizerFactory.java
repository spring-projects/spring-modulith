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

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.util.Assert;

/**
 * A factory to create {@link OutboxEventExternalizer} for {@link EventExternalizationTransport}s. Primarily intended to
 * be used with outbox implementation-specific configuration code.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Homeland Theme - https://www.youtube.com/watch?v=R-LCNXS1f1E
 */
public class OutboxEventExternalizerFactory {

	private final EventExternalizationConfiguration configuration;
	private final ApplicationEventPublisher publisher;

	/**
	 * Creates a new {@link OutboxEventExternalizerFactory} for the given {@link EventExternalizationConfiguration} and
	 * {@link ApplicationEventPublisher}.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param publisher must not be {@literal null}.
	 */
	public OutboxEventExternalizerFactory(EventExternalizationConfiguration configuration,
			ApplicationEventPublisher publisher) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");
		Assert.notNull(publisher, "ApplicationEventPublisher must not be null!");

		this.configuration = configuration;
		this.publisher = publisher;
	}

	/**
	 * Creates a {@link OutboxEventExternalizer} for the given {@link EventExternalizationTransport}.
	 *
	 * @param transport must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public OutboxEventExternalizer forTransport(EventExternalizationTransport transport) {

		Assert.notNull(transport, "EventExternalizationTransport must not be null!");

		return new OutboxEventExternalizer(configuration, publisher, transport);
	}
}
