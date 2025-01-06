/*
 * Copyright 2024-2025 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * In-memory implementation of {@link EventPublicationRepository} for testing purposes.
 *
 * @author Oliver Drotbohm
 */
public class InMemoryEventPublicationRepository
		implements EventPublicationRepository, Iterable<TargetEventPublication> {

	private static final Predicate<TargetEventPublication> IS_COMPLETED = it -> it.getCompletionDate() != null;

	private Collection<TargetEventPublication> publications = new ArrayList<>();

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#create(org.springframework.modulith.events.core.TargetEventPublication)
	 */
	@Override
	public TargetEventPublication create(TargetEventPublication publication) {

		if (!publications.contains(publication)) {
			publications.add(publication);
		}

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		publications.stream()
				.filter(it -> it.isAssociatedWith(event, identifier))
				.findFirst()
				.ifPresent(it -> it.markCompleted(completionDate));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markCompleted(java.util.UUID, java.time.Instant)
	 */
	@Override
	public void markCompleted(UUID identifier, Instant completionDate) {

		publications.stream()
				.filter(it -> it.getIdentifier().equals(identifier))
				.findFirst()
				.ifPresent(it -> it.markCompleted(completionDate));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublications()
	 */
	@Override
	public List<TargetEventPublication> findIncompletePublications() {

		return publications.stream()
				.filter(IS_COMPLETED.negate())
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsPublishedBefore(java.time.Instant)
	 */
	@Override
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {

		return publications.stream()
				.filter(IS_COMPLETED.negate())
				.filter(it -> it.getPublicationDate().isBefore(instant))
				.toList();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(Object event,
			PublicationTargetIdentifier targetIdentifier) {

		return publications.stream()
				.filter(it -> it.isAssociatedWith(event, targetIdentifier))
				.findFirst();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deletePublications(java.util.List)
	 */
	@Override
	public void deletePublications(List<UUID> identifiers) {
		publications.removeIf(it -> identifiers.contains(it.getIdentifier()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	public void deleteCompletedPublications() {
		publications.removeIf(IS_COMPLETED);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {
		publications.removeIf(IS_COMPLETED.and(it -> it.getPublicationDate().isBefore(instant)));
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<TargetEventPublication> iterator() {
		return publications.iterator();
	}
}
