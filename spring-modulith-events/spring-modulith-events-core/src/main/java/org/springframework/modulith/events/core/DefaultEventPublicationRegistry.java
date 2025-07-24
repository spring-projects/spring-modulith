/*
 * Copyright 2017-2025 the original author or authors.
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * A registry to capture event publications to {@link org.springframework.context.ApplicationListener}s. Allows to
 * register those publications, mark them as completed and lookup incomplete publications.
 *
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
public class DefaultEventPublicationRegistry
		implements DisposableBean, EventPublicationRegistry, CompletedEventPublications {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEventPublicationRegistry.class);
	private static final String REGISTER = "Registering publication of {} for {}.";

	private final EventPublicationRepository events;
	private final Clock clock;
	private final EventPublicationProperties properties;
	private final PublicationsInProgress inProgress;

	/**
	 * Creates a new {@link DefaultEventPublicationRegistry} for the given {@link EventPublicationRepository}.
	 *
	 * @param events must not be {@literal null}.
	 * @param clock must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	public DefaultEventPublicationRegistry(EventPublicationRepository events, Clock clock,
			EventPublicationProperties properties) {

		Assert.notNull(events, "EventPublicationRepository must not be null!");
		Assert.notNull(clock, "Clock must not be null!");

		this.events = events;
		this.clock = clock;
		this.properties = properties;
		this.inProgress = new PublicationsInProgress();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#store(java.lang.Object, java.util.stream.Stream)
	 */
	@Override
	public Collection<TargetEventPublication> store(Object event, Stream<PublicationTargetIdentifier> listeners) {

		return listeners.map(it -> TargetEventPublication.of(event, it, clock.instant()))
				.peek(it -> LOGGER.debug(REGISTER, it.getEvent().getClass().getName(), it.getTargetIdentifier().getValue()))
				.map(events::create)
				.map(inProgress::register)
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#findIncompletePublications()
	 */
	@Override
	public Collection<TargetEventPublication> findIncompletePublications() {
		return events.findIncompletePublications();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#findIncompletePublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public Collection<TargetEventPublication> findIncompletePublicationsOlderThan(Duration duration) {

		var reference = clock.instant().minus(duration);

		return events.findIncompletePublicationsPublishedBefore(reference);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#markProcessing(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	public void markProcessing(Object event, PublicationTargetIdentifier identifier) {
		propagateStateTransition(event, identifier, it -> events.markProcessing(it.getIdentifier()), () -> {});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(Object event, PublicationTargetIdentifier targetIdentifier) {

		Assert.notNull(event, "Domain event must not be null!");
		Assert.notNull(targetIdentifier, "Listener identifier must not be null!");

		LOGGER.debug("Marking publication of event {} to listener {} completed.", //
				event.getClass().getName(), targetIdentifier.getValue());

		var now = clock.instant();

		propagateStateTransitionAndConclude(event, targetIdentifier, it -> events.markCompleted(it, now),
				() -> events.markCompleted(event, targetIdentifier, now));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#markFailed(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	public void markFailed(Object event, PublicationTargetIdentifier targetIdentifier) {

		propagateStateTransitionAndConclude(event, targetIdentifier, it -> events.markFailed(it.getIdentifier()), () -> {});

		inProgress.unregister(event, targetIdentifier);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRegistry#deleteCompletedPublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public void deleteCompletedPublicationsOlderThan(Duration duration) {

		Assert.notNull(duration, "Duration must not be null!");

		events.deleteCompletedPublicationsBefore(clock.instant().minus(duration));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.CompletedEventPublications#findAll()
	 */
	@Override
	public Collection<? extends TargetEventPublication> findAll() {
		return events.findCompletedPublications();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.CompletedEventPublications#deletePublications(java.util.function.Predicate)
	 */
	@Override
	public void deletePublications(Predicate<EventPublication> filter) {

		var identifiers = findAll().stream()
				.filter(filter)
				.map(TargetEventPublication::getIdentifier)
				.toList();

		events.deletePublications(identifiers);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.CompletedEventPublications#deletePublicationsOlderThan(java.time.Duration)
	 */
	@Override
	public void deletePublicationsOlderThan(Duration duration) {

		var now = clock.instant();

		events.deleteCompletedPublicationsBefore(now.minus(duration));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#processIncompletePublications(java.util.function.Predicate, java.util.function.Consumer, java.time.Duration)
	 */
	@Override
	public void processIncompletePublications(Predicate<EventPublication> filter,
			Consumer<TargetEventPublication> consumer, @Nullable Duration duration) {

		var message = duration != null ? " older than %s".formatted(duration) : "";

		LOGGER.debug("Looking up incomplete event publications {}… ", message);

		var publications = duration == null //
				? findIncompletePublications() //
				: findIncompletePublicationsOlderThan(duration);

		LOGGER.debug(getConfirmationMessage(publications) + " found.");

		processPublications(publications, filter, consumer);
	}

	/**
	 * @param publications
	 * @param filter
	 * @param consumer
	 */
	private void processPublications(Collection<TargetEventPublication> publications, Predicate<EventPublication> filter,
			Consumer<TargetEventPublication> consumer) {

		publications.stream() //
				.filter(filter) //
				.forEach(it -> {

					if (!events.markResubmitted(it.getIdentifier(), clock.instant())) {
						return;
					}

					LOGGER.debug("Resubmitting event publication %s.".formatted(it.getIdentifier()));

					try {

						inProgress.register(it);
						consumer.accept(it);

					} catch (Exception o_O) {

						inProgress.unregister(it);

						if (LOGGER.isInfoEnabled()) {
							LOGGER.info("Error republishing event publication %s.".formatted(it), o_O);
						}
					}
				});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#processFailedPublications(org.springframework.modulith.events.ResubmissionOptions, java.util.function.Consumer)
	 */
	@Override
	public void processFailedPublications(ResubmissionOptions options, Consumer<TargetEventPublication> consumer) {

		var currentlyResubmitted = events.countByStatus(Status.RESUBMITTED);

		if (currentlyResubmitted >= options.getMaxInFlight()) {

			LOGGER.info("Skipping resubmission as only %s should be resubmitted in parallel and currently %s are.",
					options.getMaxInFlight(), currentlyResubmitted);
			return;
		}

		var criteria = new EventPublicationRepository.IncompleteCriteria() {

			@Override
			public Instant getInstant() {
				return clock.instant().minus(options.getMinAge());
			}

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.events.core.EventPublicationRepository.IncompleteCriteria#getMaxItemsToRead()
			 */
			@Override
			public int getMaxItemsToRead() {
				return Math.min(options.getBatchSize(), options.getBatchSize() - currentlyResubmitted);
			}
		};

		processPublications(events.findFailedPublications(criteria), options.getFilter(), consumer);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#markStalePublicationsFailed()
	 */
	@Override
	public void markStalePublicationsFailed() {

		markFailed(Status.RESUBMITTED, properties.getResubmissionStaleness());
		markFailed(Status.PROCESSING, properties.getProcessingStaleness());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	@Override
	public void destroy() {

		var publications = events.findIncompletePublications();

		if (publications.isEmpty()) {

			LOGGER.info("No publications outstanding!");
			return;
		}

		LOGGER.info("Shutting down with the following publications left unfinished:");

		for (int i = 0; i < publications.size(); i++) {

			var prefix = i + 1 == publications.size() ? "└─" : "├─";
			var it = publications.get(i);

			LOGGER.info("{} {} - {}", prefix, it.getEvent().getClass().getName(), it.getTargetIdentifier().getValue());
		}
	}

	/**
	 * Returns all {@link PublicationsInProgress}.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	PublicationsInProgress getPublicationsInProgress() {
		return inProgress;
	}

	/**
	 * Marks the given {@link TargetEventPublication} as failed.
	 *
	 * @param publication must not be {@literal null}.
	 * @see #markFailed(Object, PublicationTargetIdentifier)
	 * @since 1.3
	 */
	void markFailed(TargetEventPublication publication) {

		Assert.notNull(publication, "TargetEventPublication must not be null!");

		markFailed(publication.getEvent(), publication.getTargetIdentifier());
	}

	private void propagateStateTransitionAndConclude(Object event, PublicationTargetIdentifier identifier,
			Consumer<TargetEventPublication> consumer,
			Runnable runnable) {

		Runnable concluding = () -> {
			runnable.run();
			inProgress.unregister(event, identifier);
		};

		propagateStateTransition(event, identifier, consumer.andThen(inProgress::unregister), concluding);
	}

	private void propagateStateTransition(Object event, PublicationTargetIdentifier identifier,
			Consumer<TargetEventPublication> consumer, Runnable runnable) {

		inProgress.getPublication(event, identifier)
				.ifPresentOrElse(consumer::accept, runnable);
	}

	private void markFailed(Status status, Duration duration) {

		var reference = clock.instant().minus(duration);
		var result = events.findByStatus(status).stream()
				.filter(it -> it.getPublicationDate().isBefore(reference))
				.map(TargetEventPublication::getIdentifier).toList();

		if (result.isEmpty()) {

			LOGGER.info("No stale publications of status {} found.", status);
			return;
		}

		LOGGER.info("Marking stale publications of status {} older than {} as failed:", status, duration);

		result.stream()
				.peek(it -> LOGGER.info("- {}", it))
				.forEach(events::markFailed);
	}

	private static String getConfirmationMessage(Collection<?> publications) {

		var size = publications.size();

		return switch (publications.size()) {
			case 0 -> "No publication";
			case 1 -> "1 publication";
			default -> size + " publications";
		};
	}

	/**
	 * All {@link TargetEventPublication}s currently processed.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.3
	 */
	static class PublicationsInProgress implements Iterable<TargetEventPublication> {

		private final Map<Key, TargetEventPublication> publications = new ConcurrentHashMap<>();

		/**
		 * Registers the given {@link TargetEventPublication} as currently processed.
		 *
		 * @param publication must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		TargetEventPublication register(TargetEventPublication publication) {

			Assert.notNull(publication, "TargetEventPublication must not be null!");

			publications.put(new Key(publication), publication);

			return publication;
		}

		/**
		 * Unregisters the {@link TargetEventPublication} associated with the given event and
		 * {@link PublicationTargetIdentifier}.
		 *
		 * @param event must not be {@literal null}.
		 * @param identifier must not be {@literal null}.
		 */
		void unregister(Object event, PublicationTargetIdentifier identifier) {

			Assert.notNull(event, "Event must not be null!");
			Assert.notNull(identifier, "PublicationTargetIdentifier must not be null!");

			publications.remove(new Key(event, identifier));
		}

		/**
		 * Unregisters the {@link TargetEventPublication}..
		 *
		 * @param publication must not be {@literal null}.
		 */
		void unregister(TargetEventPublication publication) {

			Assert.notNull(publication, "TargetEventPublication must not be null!");

			publications.remove(new Key(publication));
		}

		/**
		 * Returns the {@link TargetEventPublication} associated with the given event and
		 * {@link PublicationTargetIdentifier}.
		 *
		 * @param event must not be {@literal null}.
		 * @param identifier must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		Optional<TargetEventPublication> getPublication(Object event, PublicationTargetIdentifier identifier) {

			Assert.notNull(event, "Event must not be null!");
			Assert.notNull(identifier, "PublicationTargetIdentifier must not be null!");

			return Optional.ofNullable(publications.get(new Key(event, identifier)));
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Iterable#iterator()
		 */
		@Override
		public Iterator<TargetEventPublication> iterator() {
			return publications.values().iterator();
		}

		private record Key(Object event, PublicationTargetIdentifier identifier) {

			public Key(TargetEventPublication publication) {
				this(publication.getEvent(), publication.getTargetIdentifier());
			}

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.events.core.DefaultEventPublicationRegistry.PublicationsInProgress.Key#equals(java.lang.Object)
			 */
			@Override
			public final boolean equals(Object obj) {

				if (obj == this) {
					return true;
				}

				if (!(obj instanceof Key that)) {
					return false;
				}

				return this.event == that.event
						&& this.identifier.equals(that.identifier);
			}

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.events.core.DefaultEventPublicationRegistry.PublicationsInProgress.Key#hashCode()
			 */
			@Override
			public final int hashCode() {

				int result = 7;

				result += 31 * System.identityHashCode(event);
				result += 31 * identifier.hashCode();

				return result;
			}
		}
	}
}
