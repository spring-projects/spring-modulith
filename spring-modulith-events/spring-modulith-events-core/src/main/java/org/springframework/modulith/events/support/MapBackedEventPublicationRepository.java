/*
 * Copyright 2017-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.events.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.PublicationTargetIdentifier;

/**
 * Map based {@link EventPublicationRepository}, for testing purposes only.
 *
 * @author Björn Kieling, Dmitry Belyaev
 */
public class MapBackedEventPublicationRepository implements EventPublicationRepository {

	private final List<CompletableEventPublication> events = new ArrayList<>();

	@Override
	public EventPublication create(EventPublication publication) {
		events.add(CompletableEventPublication.of(publication.getEvent(), publication.getTargetIdentifier()));
		return publication;
	}

	@Override
	public EventPublication updateCompletionDate(CompletableEventPublication publication) {
		findByEventAndTargetIdentifier(publication.getEvent(), publication.getTargetIdentifier())
				.ifPresent(eventPublication -> ((CompletableEventPublication) eventPublication).markCompleted());
		return publication;
	}

	@Override
	public List<EventPublication> findByCompletionDateIsNull() {
		return events.stream()
				.filter(publication -> !publication.isPublicationCompleted())
				.collect(Collectors.toList());
	}

	@Override
	public Optional<EventPublication> findByEventAndTargetIdentifier(Object event, PublicationTargetIdentifier targetIdentifier) {
		return events.stream()
				.filter(publication ->
						publication.equals(publication.getEvent()) && publication.getTargetIdentifier().equals(targetIdentifier))
				.map(EventPublication.class::cast)
				.findAny();
	}
}
