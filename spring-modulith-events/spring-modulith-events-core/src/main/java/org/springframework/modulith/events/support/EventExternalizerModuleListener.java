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

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.Assert;

/**
 * An {@link ApplicationModuleListener} that's only applied if the event to be externalized is supposed to be
 * externalized in the first place. Note, that this <em>needs</em> to be a {@link Component} to make sure it is
 * considered an event listener, as without the annotation Spring Framework would skip it as it lives in the
 * {@code org.springframework} package.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @see ConditionalEventListener
 * @soundtrack Kamasi Washington - Truth - https://www.youtube.com/watch?v=rtW1S5EbHgU
 */
@Component
public class EventExternalizerModuleListener extends TransportAwareEventExternalizer
		implements ConditionalEventListener {

	private final EventExternalizationConfiguration configuration;

	/**
	 * Creates a new {@link EventExternalizerModuleListener} for the given {@link EventExternalizationConfiguration} and
	 * {@link EventExternalizationTransport}.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param transport must not be {@literal null}.
	 */
	public EventExternalizerModuleListener(EventExternalizationConfiguration configuration,
			EventExternalizationTransport transport) {

		super(configuration, transport);

		Assert.notNull(transport, "EventExternalizationTransport must not be null!");

		this.configuration = configuration;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.ConditionalEventListener#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(Object event) {
		return configuration.supports(event);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.support.EventExternalizationSupport#externalize(java.lang.Object)
	 */
	@Override
	@ApplicationModuleListener(propagation = Propagation.SUPPORTS)
	public CompletableFuture<?> externalize(Object event) {
		return super.externalize(event);
	}
}
