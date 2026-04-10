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

import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.util.Assert;

/**
 * An {@link EventExternalizationSupport} delegating to a {@link EventExternalizationTransport} for the actual
 * externalization.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Roey Marquis II. - Shadows within - https://www.youtube.com/watch?v=XD_pC3XlOR4
 */
abstract class TransportAwareEventExternalizer extends EventExternalizerSupport {

	private final EventExternalizationTransport transport;

	/**
	 * Creates a new {@link DelegatingEventExternalizer} for the given {@link EventExternalizationConfiguration} and
	 * {@link EventExternalizationTransport} implementing the actual externalization.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param transport must not be {@literal null}.
	 */
	public TransportAwareEventExternalizer(EventExternalizationConfiguration configuration,
			EventExternalizationTransport transport) {

		super(configuration);

		Assert.notNull(transport, "EventExternalizationTransport must not be null!");

		this.transport = transport;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.support.EventExternalizationSupport#externalize(org.springframework.modulith.events.RoutingTarget, java.lang.Object)
	 */
	@Override
	protected CompletableFuture<?> externalize(Object payload, RoutingTarget target) {

		try {

			var result = transport.externalize(payload, target);

			return result != null
					? result
					: CompletableFuture.failedFuture(new IllegalStateException("Delegate must not return null!"));

		} catch (RuntimeException o_O) {
			return CompletableFuture.failedFuture(o_O);
		}
	}
}
