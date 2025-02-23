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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
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
	private final PublicationsInProgress inProgress;

	/**
	 * Creates a new {@link DefaultEventPublicationRegistry} for the given {@link EventPublicationRepository}.
	 *
	 * @param events must not be {@literal null}.
	 * @param clock must not be {@literal null}.
	 */
	public DefaultEventPublicationRegistry(EventPublicationRepository events, Clock clock) {

		Assert.notNull(events, "EventPublicationRepository must not be null!");
		Assert.notNull(clock, "Clock must not be null!");

		this.events = events;
		this.clock = clock;
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
	 * @see org.springframework.modulith.events.EventPublicationRegistry#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(Object event, PublicationTargetIdentifier targetIdentifier) {

		Assert.notNull(event, "Domain event must not be null!");
		Assert.notNull(targetIdentifier, "Listener identifier must not be null!");

		LOGGER.debug("Marking publication of event {} to listener {} completed.", //
				event.getClass().getName(), targetIdentifier.getValue());

		Instant now = clock.instant();
		Runnable fallback = () -> events.markCompleted(event, targetIdentifier, now);
		Consumer<TargetEventPublication> optimized = it -> {
			events.markCompleted(it.getIdentifier(), now);
			inProgress.unregister(event, targetIdentifier);
		};

		inProgress.getPublication(event, targetIdentifier)
				.ifPresentOrElse(optimized, fallback);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRegistry#markFailed(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	public void markFailed(Object event, PublicationTargetIdentifier targetIdentifier) {
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

		publications.stream() //
				.filter(filter) //
				.forEach(it -> {

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

		private final Set<TargetEventPublication> publications = ConcurrentHashMap.newKeySet();

		/**
		 * Registers the given {@link TargetEventPublication} as currently processed.
		 *
		 * @param publication must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		TargetEventPublication register(TargetEventPublication publication) {

			Assert.notNull(publication, "TargetEventPublication must not be null!");

			publications.add(publication);

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

			getPublication(event, identifier)
					.ifPresent(publications::remove);
		}

		/**
		 * Unregisters the {@link TargetEventPublication}..
		 *
		 * @param publication must not be {@literal null}.
		 */
		void unregister(TargetEventPublication publication) {

			Assert.notNull(publication, "TargetEventPublication must not be null!");

			publications.remove(publication);
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

			return publications.stream()
					.filter(it -> it.isAssociatedWith(event, identifier))
					.findFirst();
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Iterable#iterator()
		 */
		@Override
		public Iterator<TargetEventPublication> iterator() {
			return new HashSet<>(publications).iterator();
		}
	}
}
