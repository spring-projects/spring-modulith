/*
 * Copyright 2022-2024 the original author or authors.
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
	 * Marks the given {@link TargetEventPublication} as completed.
	 *
	 * @param publication must not be {@literal null}.
	 * @param completionDate must not be {@literal null}.
	 */
	default void markCompleted(TargetEventPublication publication, Instant completionDate) {

		Assert.notNull(publication, "EventPublication must not be null!");
		Assert.notNull(completionDate, "Instant must not be null!");

		publication.markCompleted(completionDate);

		markCompleted(publication.getEvent(), publication.getTargetIdentifier(), completionDate);
	}

	/**
	 * Marks the publication for the given event and {@link PublicationTargetIdentifier} to be completed at the given
	 * {@link Instant}.
	 *
	 * @param event must not be {@literal null}.
	 * @param identifier must not be {@literal null}.
	 * @param completionDate must not be {@literal null}.
	 */
	void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate);

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
}
