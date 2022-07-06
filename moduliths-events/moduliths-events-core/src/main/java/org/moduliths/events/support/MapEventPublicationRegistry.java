/*
 * Copyright 2017-2020 the original author or authors.
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
package org.moduliths.events.support;

import lombok.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.moduliths.events.CompletableEventPublication;
import org.moduliths.events.EventPublication;
import org.moduliths.events.EventPublicationRegistry;
import org.moduliths.events.PublicationTargetIdentifier;

/**
 * Map based {@link EventPublicationRegistry}, for testing purposes only.
 *
 * @author Oliver Drotbohm
 */
public class MapEventPublicationRegistry implements EventPublicationRegistry {

	private final Map<Key, CompletableEventPublication> events = new HashMap<>();

	/*
	 * (non-Javadoc)
	 * @see org.springframework.events.EventPublicationRegistry#findIncompletePublications()
	 */
	@Override
	public Iterable<EventPublication> findIncompletePublications() {

		return events.entrySet().stream()//
				.filter(it -> !it.getValue().isPublicationCompleted())//
				.map(it -> it.getValue())//
				.collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.events.EventPublicationRegistry#store(java.lang.Object, java.util.Collection)
	 */
	@Override
	public void store(Object event, Stream<PublicationTargetIdentifier> identifiers) {

		identifiers.forEach(id -> {
			events.computeIfAbsent(Key.of(event, id), it -> CompletableEventPublication.of(event, id));
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.events.EventPublicationRegistry#markCompleted(java.lang.Object, org.springframework.events.PublicationTargetIdentifier)
	 */
	@Override
	public void markCompleted(Object event, PublicationTargetIdentifier id) {
		events.computeIfPresent(Key.of(event, id), (__, value) -> value.markCompleted());
	}

	@Value(staticConstructor = "of")
	private static class Key {

		Object event;
		PublicationTargetIdentifier identifier;
	}
}
