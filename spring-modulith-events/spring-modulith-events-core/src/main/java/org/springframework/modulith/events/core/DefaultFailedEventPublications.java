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
package org.springframework.modulith.events.core;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.AbandonPolicy;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link FailedEventPublications}, resubmitting failed {@link EventPublication}s and applying
 * {@link AbandonPolicy} decisions to them via the {@link EventPublicationRegistry}. Also bridges the deprecated
 * {@link IncompleteEventPublications} and triggers resubmission of outstanding publications on startup via
 * {@link #afterSingletonsInstantiated()} if configured to do so.
 *
 * @author Oliver Drotbohm
 * @author Seonwoo Jung
 * @since 2.2
 */
@SuppressWarnings("removal")
public class DefaultFailedEventPublications implements FailedEventPublications, IncompleteEventPublications,
		SmartInitializingSingleton {

	static final String REPUBLISH_ON_RESTART = "spring.modulith.events.republish-outstanding-events-on-restart";

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFailedEventPublications.class);

	private final Supplier<EventPublicationRegistry> registry;
	private final Supplier<TransactionalEventListeners> listeners;
	private final Supplier<Environment> environment;

	/**
	 * Creates a new {@link DefaultFailedEventPublications} for the given {@link EventPublicationRegistry},
	 * {@link TransactionalEventListeners} and {@link Environment}.
	 *
	 * @param registry must not be {@literal null}.
	 * @param listeners must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	public DefaultFailedEventPublications(Supplier<EventPublicationRegistry> registry,
			Supplier<TransactionalEventListeners> listeners, Supplier<Environment> environment) {

		Assert.notNull(registry, "EventPublicationRegistry must not be null!");
		Assert.notNull(listeners, "TransactionalEventListeners must not be null!");
		Assert.notNull(environment, "Environment must not be null!");

		this.registry = registry;
		this.listeners = listeners;
		this.environment = environment;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.FailedEventPublications#resubmit(org.springframework.modulith.events.ResubmissionOptions)
	 */
	@Override
	public void resubmit(ResubmissionOptions options) {
		registry.get().processFailedPublications(options, this::invokeTargetListener);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.FailedEventPublications#applyAbandonPolicy()
	 */
	@Override
	public void applyAbandonPolicy() {
		registry.get().applyAbandonPolicy(null);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.FailedEventPublications#applyAbandonPolicy(org.springframework.modulith.events.AbandonPolicy)
	 */
	@Override
	public void applyAbandonPolicy(AbandonPolicy policy) {

		Assert.notNull(policy, "AbandonPolicy must not be null!");

		registry.get().applyAbandonPolicy(policy);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.IncompleteEventPublications#resubmitIncompletePublications(java.util.function.Predicate)
	 */
	@Override
	public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
		doResubmitUncompletedPublicationsOlderThan(null, filter);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.IncompleteEventPublications#resubmitIncompletePublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public void resubmitIncompletePublicationsOlderThan(Duration duration) {
		doResubmitUncompletedPublicationsOlderThan(duration, __ -> true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.IncompleteEventPublications#resubmitIncompletePublications(org.springframework.modulith.events.ResubmissionOptions)
	 */
	@Override
	public void resubmitIncompletePublications(ResubmissionOptions options) {
		resubmit(options);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.SmartInitializingSingleton#afterSingletonsInstantiated()
	 */
	@Override
	public void afterSingletonsInstantiated() {

		var env = environment.get();

		if (!Boolean.TRUE.equals(env.getProperty(REPUBLISH_ON_RESTART, Boolean.class))) {
			return;
		}

		resubmitIncompletePublications(__ -> true);
	}

	private void doResubmitUncompletedPublicationsOlderThan(@Nullable Duration duration,
			Predicate<EventPublication> filter) {

		registry.get().processIncompletePublications(filter, this::invokeTargetListener, duration);
	}

	private void invokeTargetListener(TargetEventPublication publication) {
		listeners.get().invoke(publication, this::markFailed);
	}

	private void markFailed(TargetEventPublication publication) {

		LOGGER.error("Listener {} not found! Skipping invocation and leaving event publication {} failed.",
				publication.getTargetIdentifier(), publication.getIdentifier());

		registry.get().markFailed(publication.getEvent(), publication.getTargetIdentifier());
	}
}
