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
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalized;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.util.Assert;

/**
 * Fundamental support for event externalization. Considers the configured {@link EventExternalizationConfiguration}
 * before handing off the ultimate message publication to {@link #externalize(Object, RoutingTarget)}.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Barbara Buchholz - Coyote - https://www.youtube.com/watch?v=1YYZVt3blQY
 */
abstract class EventExternalizerSupport {

	private static final Logger logger = LoggerFactory.getLogger(EventExternalizerSupport.class);

	private final EventExternalizationConfiguration configuration;
	private final EvaluationContext context;
	private final Semaphore semaphore = new Semaphore(1);

	/**
	 * Creates a new {@link EventExternalizerSupport} for the given {@link EventExternalizationConfiguration}, not
	 * resolving bean references in routing target and key expressions.
	 *
	 * @param configuration must not be {@literal null}.
	 * @deprecated since 2.2, 2.1.2, for removal in 2.3. Use
	 *             {@link #EventExternalizerSupport(EventExternalizationConfiguration, BeanFactory)} instead.
	 */
	@Deprecated(since = "2.2, 2.1.2", forRemoval = true)
	protected EventExternalizerSupport(EventExternalizationConfiguration configuration) {
		this(configuration, new StandardEvaluationContext());
	}

	/**
	 * Creates a new {@link EventExternalizerSupport} for the given {@link EventExternalizationConfiguration}, resolving
	 * routing target and key expressions (which may refer to beans) against the original event using an
	 * {@link EvaluationContext} backed by the given {@link BeanFactory}.
	 *
	 * @param configuration must not be {@literal null}.
	 * @param beanFactory must not be {@literal null}.
	 * @since 2.2, 2.1.2
	 */
	protected EventExternalizerSupport(EventExternalizationConfiguration configuration, BeanFactory beanFactory) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");
		Assert.notNull(beanFactory, "BeanFactory must not be null!");

		var context = new StandardEvaluationContext();
		context.setBeanResolver(new BeanFactoryResolver(beanFactory));

		this.configuration = configuration;
		this.context = context;
	}

	private EventExternalizerSupport(EventExternalizationConfiguration configuration, EvaluationContext context) {

		Assert.notNull(configuration, "EventExternalizationConfiguration must not be null!");
		Assert.notNull(context, "EvaluationContext must not be null!");

		this.configuration = configuration;
		this.context = context;
	}

	/**
	 * Externalizes the given event.
	 *
	 * @param event must not be {@literal null}.
	 * @return the externalization result, will never be {@literal null}.
	 */
	public CompletableFuture<?> externalize(Object event) {

		Assert.notNull(event, "Object must not be null!");

		if (!configuration.supports(event)) {
			return CompletableFuture.completedFuture(null);
		}

		var target = configuration.determineTarget(event);
		var resolved = resolve(target, event);
		var mapped = configuration.map(event);

		if (logger.isTraceEnabled()) {
			logger.trace("Externalizing event of type {} to {}, payload: {}).", event.getClass(), resolved, mapped);
		} else if (logger.isDebugEnabled()) {
			logger.debug("Externalizing event of type {} to {}.", event.getClass(), resolved);
		}

		return configuration.serializeExternalization()
				? doExternalizeSerialized(event, mapped, resolved)
				: doExternalize(event, mapped, resolved);
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

	/**
	 * Resolves dynamic target and key expressions declared on the given {@link RoutingTarget} against the given
	 * (original, unmapped) event, so that a mapping applied downstream cannot affect routing.
	 *
	 * @param target must not be {@literal null}.
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private RoutingTarget resolve(RoutingTarget target, Object event) {

		if (!target.hasExpression()) {
			return target;
		}

		var routing = BrokerRouting.of(target, context);
		var resolved = RoutingTarget.forTarget(routing.getTarget(event));

		return target.getKey() == null ? resolved.withoutKey() : resolved.andKey(routing.getKey(event));
	}
}
