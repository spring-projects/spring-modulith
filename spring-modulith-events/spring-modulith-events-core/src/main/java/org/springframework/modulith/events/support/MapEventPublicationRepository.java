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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.PublicationTargetIdentifier;

/**
 * Map based {@link EventPublicationRepository}, for testing purposes only.
 *
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
public class MapEventPublicationRepository implements EventPublicationRepository {

	private final Map<Key, CompletableEventPublication> events = new TreeMap<>();

	@Override
	public EventPublication create(EventPublication publication) {

		return events.computeIfAbsent(Key.of(publication),
				it -> CompletableEventPublication.of(it.getEvent(), it.getIdentifier()));
	}

	@Override
	public EventPublication update(CompletableEventPublication publication) {

		var result = events.computeIfPresent(Key.of(publication), (__, it) -> it.markCompleted());

		if (result == null) {
			throw new IllegalArgumentException("Couldn't find publication %s!".formatted(publication));
		}

		return result;
	}

	@Override
	public List<EventPublication> findIncompletePublications() {

		return events.values().stream() //
				.filter(it -> !it.isPublicationCompleted())
				.map(EventPublication.class::cast)
				.toList();
	}

	@Override
	public Optional<EventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
			Object event, PublicationTargetIdentifier targetIdentifier) {

		return Optional.ofNullable(events.get(new Key(event, targetIdentifier)));
	}

	@Value
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class Key {

		Object event;
		PublicationTargetIdentifier identifier;

		static Key of(EventPublication publication) {
			return new Key(publication.getEvent(), publication.getTargetIdentifier());
		}
	}
}
