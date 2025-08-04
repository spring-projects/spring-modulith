/*
 * Copyright 2022-2025 the original author or authors.
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.util.Assert;

/**
 * Repository to store {@link TargetEventPublication}s.
 *
 * @author Björn Kieling
 * @author Dmitry Belyaev
 * @author Oliver Drotbohm
 */
public interface EventPublicationRepository {

	/**
	 * Persists the given {@link TargetEventPublication}.
	 *
	 * @param publication must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	TargetEventPublication create(TargetEventPublication publication);

	/**
	 * Marks the {@link org.springframework.modulith.events.EventPublication} with the given identifier as processing.
	 *
	 * @param identifier must not be {@literal null}.
	 * @since 2.0
	 * @see org.springframework.modulith.events.EventPublication.Status#PROCESSING
	 */
	default void markProcessing(UUID identifier) {}

	/**
	 * Marks the given {@link TargetEventPublication} as completed.
	 *
	 * @param publication must not be {@literal null}.
	 * @param completionDate must not be {@literal null}.
	 */
	default void markCompleted(TargetEventPublication publication, Instant completionDate) {

		Assert.notNull(publication, "EventPublication must not be null!");
		Assert.notNull(completionDate, "Instant must not be null!");

		publication.markCompleted(completionDate);

		markCompleted(publication.getIdentifier(), completionDate);
	}

	/**
	 * Marks the publication for the given event and {@link PublicationTargetIdentifier} to be completed at the given
	 * {@link Instant}.
	 *
	 * @param event must not be {@literal null}.
	 * @param identifier must not be {@literal null}.
	 * @param completionDate must not be {@literal null}.
	 * @see org.springframework.modulith.events.EventPublication.Status#COMPLETED
	 */
	void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate);

	/**
	 * Marks the publication with the given identifier completed at the given {@link Instant}.
	 *
	 * @param identifier must not be {@literal null}.
	 * @param completionDate must not be {@literal null}.
	 * @since 1.3
	 * @see org.springframework.modulith.events.EventPublication.Status#COMPLETED
	 */
	void markCompleted(UUID identifier, Instant completionDate);

	/**
	 * Marks the {@link org.springframework.modulith.events.EventPublication} with the given identifier as failed.
	 *
	 * @param identifier must not be {@literal null}.
	 * @since 2.0
	 * @see org.springframework.modulith.events.EventPublication.Status#FAILED
	 */
	default void markFailed(UUID identifier) {}

	/**
	 * Marks the {@link org.springframework.modulith.events.EventPublication} with the given identifier as resubmitted.
	 * Returns {@literal false} in case the update was not successful, which usually means that the instance identified
	 * might have been claimed for re-submission by another application instance.
	 *
	 * @param identifier must not be {@literal null}.
	 * @param resubmissionDate must not be {@literal null}.
	 * @return whether the {@link org.springframework.modulith.events.EventPublication} with the given identifier was
	 *         marked as resubmitted.
	 * @since 2.0
	 * @see org.springframework.modulith.events.EventPublication.Status#RESUBMITTED
	 */
	default boolean markResubmitted(UUID identifier, Instant resubmissionDate) {
		return true;
	}

	/**
	 * Returns all {@link TargetEventPublication}s that have not been completed yet.
	 *
	 * @return will never be {@literal null}.
	 */
	List<TargetEventPublication> findIncompletePublications();

	/**
	 * Returns all {@link TargetEventPublication}s that have not been completed and were published before the given
	 * {@link Instant}.
	 *
	 * @param instant must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant);

	/**
	 * Return the incomplete {@link TargetEventPublication} for the given serialized event and listener identifier.
	 *
	 * @param event must not be {@literal null}.
	 * @param targetIdentifier must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier( //
			Object event, PublicationTargetIdentifier targetIdentifier);

	/**
	 * Returns all completed event publications currently found in the system.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.1.2
	 */
	default List<TargetEventPublication> findCompletedPublications() {
		throw new UnsupportedOperationException(
				"Your store implementation does not support looking up completed publications!");
	}

	/**
	 * Deletes all publications with the given identifiers.
	 *
	 * @param identifiers must not be {@literal null}.
	 * @since 1.1
	 */
	void deletePublications(List<UUID> identifiers);

	/**
	 * Deletes all publications that were already marked as completed.
	 */
	void deleteCompletedPublications();

	/**
	 * Deletes all publication that were already marked as completed with a completion date before the given one.
	 *
	 * @param instant must not be {@literal null}.
	 */
	void deleteCompletedPublicationsBefore(Instant instant);

	/**
	 * @param criteria must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 2.0
	 */
	default List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {

		Assert.notNull(criteria, "FailedCriteria must not be null!");

		return Collections.emptyList();
	}

	/**
	 * Returns all {@link TargetEventPublication}s with the given
	 * {@link org.springframework.modulith.events.EventPublication.Status}.
	 *
	 * @param status must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 2.0
	 */
	default List<TargetEventPublication> findByStatus(Status status) {

		Assert.notNull(status, "Status must not be null!");

		return Collections.emptyList();
	}

	/**
	 * Returns the number of {@link org.springframework.modulith.events.EventPublication}s for the given status.
	 *
	 * @param status must not be {@literal null}.
	 * @return must not be negative
	 * @since 2.0
	 */
	default int countByStatus(Status status) {
		return 0;
	}

	static class FailedCriteria {

		public static FailedCriteria ALL = new FailedCriteria(-1, null);

		private final long maxItemsToRead;
		private final @Nullable Instant publicationDateReference;

		private FailedCriteria(long maxItemsToRead, @Nullable Instant publicationDateReference) {
			this.maxItemsToRead = maxItemsToRead;
			this.publicationDateReference = publicationDateReference;
		}

		/**
		 * The reference date to use as cutoff when selecting failed
		 * {@link org.springframework.modulith.events.EventPublication}s, i.e. only older publications will be selected.
		 *
		 * @return can be {@literal null}.
		 */
		@Nullable
		public Instant getPublicationDateReference() {
			return publicationDateReference;
		}

		public FailedCriteria withPublicationsPublishedBefore(Instant reference) {
			return new FailedCriteria(maxItemsToRead, reference);
		}

		/**
		 * The number of {@link org.springframework.modulith.events.EventPublication}s to read. Return -1 to indicate you
		 * want to read all items.
		 */
		public long getMaxItemsToRead() {
			return maxItemsToRead;
		}

		public FailedCriteria withItemsToRead(long itemsToRead) {
			return new FailedCriteria(itemsToRead, publicationDateReference);
		}
	}
}
