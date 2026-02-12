/*
 * Copyright 2023-2026 the original author or authors.
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
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalized;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.core.ConditionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.Assert;

/**
 * Fundamental support for event externalization. Considers the configured {@link EventExternalizationConfiguration}
 * before handing off the ultimate message publication to {@link #externalize(Object, RoutingTarget)}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
abstract class EventExternalizationSupport implements ConditionalEventListener {

	private static final Logger logger = LoggerFactory.getLogger(EventExternalizationSupport.class);

	private final EventExternalizationConfiguration configuration;
	private final Semaphore semaphore = new Semaphore(1);

	/**
	 * Creates a new {@link EventExternalizationSupport} for the given {@link EventExternalizationConfiguration}.
	 *
	 * @param configuration must not be {@literal null}.
	 */
	protected EventExternalizationSupport(EventExternalizationConfiguration configuration) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");

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

	/**
	 * Externalizes the given event.
	 *
	 * @param event must not be {@literal null}.
	 * @return the externalization result, will never be {@literal null}.
	 */
	@ApplicationModuleListener(propagation = Propagation.SUPPORTS)
	public CompletableFuture<?> externalize(Object event) {

		Assert.notNull(event, "Object must not be null!");

		if (!configuration.supports(event)) {
			return CompletableFuture.completedFuture(null);
		}

		var target = configuration.determineTarget(event);
		var mapped = configuration.map(event);

		if (logger.isTraceEnabled()) {
			logger.trace("Externalizing event of type {} to {}, payload: {}).", event.getClass(), target, mapped);
		} else if (logger.isDebugEnabled()) {
			logger.debug("Externalizing event of type {} to {}.", event.getClass(), target);
		}

		return configuration.serializeExternalization()
				? doExternalizeSerialized(event, mapped, target)
				: doExternalize(event, mapped, target);
	}

	/**
	 * Publish the given payload to the given {@link RoutingTarget}.
	 *
	 * @param payload must not be {@literal null}.
	 * @param target must not be {@literal null}.
	 * @return the externalization result, will never be {@literal null}.
	 */
	protected abstract CompletableFuture<?> externalize(Object payload, RoutingTarget target);

	private CompletableFuture<?> doExternalizeSerialized(Object event, Object mapped, RoutingTarget target) {

		try {

			semaphore.acquire();

			return doExternalize(event, mapped, target)
					.whenComplete((__, ___) -> semaphore.release());

		} catch (InterruptedException o_O) {

			semaphore.release();
			throw new RuntimeException(o_O);
		}
	}

	private CompletableFuture<?> doExternalize(Object event, Object mapped, RoutingTarget target) {

		return externalize(mapped, target)
				.thenApply(it -> new EventExternalized<>(event, mapped, target, it));
	}
}
