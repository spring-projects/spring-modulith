/*
 * Copyright 2017-2022 the original author or authors.
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
package org.springframework.modulith.events;

import java.util.stream.Stream;

import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;

/**
 * A registry to capture event publications to {@link ApplicationListener}s. Allows to register those publications, mark
 * them as completed and lookup incomplete publications.
 *
 * @author Oliver Drotbohm
 */
public interface EventPublicationRegistry {

	/**
	 * Stores {@link EventPublication}s for the given event and {@link ApplicationListener}s.
	 *
	 * @param event must not be {@literal null}.
	 * @param listeners must not be {@literal null}.
	 */
	void store(Object event, Stream<PublicationTargetIdentifier> listeners);

	/**
	 * Marks the publication for the given event and {@link PublicationTargetIdentifier} as completed.
	 *
	 * @param event must not be {@literal null}.
	 * @param listener must not be {@literal null}.
	 */
	void markCompleted(Object event, PublicationTargetIdentifier listener);

	/**
	 * Marks the given {@link EventPublication} as completed.
	 *
	 * @param publication must not be {@literal null}.
	 */
	default void markCompleted(EventPublication publication) {

		Assert.notNull(publication, "Publication must not be null!");

		markCompleted(publication.getEvent(), publication.getTargetIdentifier());
	}

	/**
	 * Returns all {@link EventPublication}s that have not been completed yet.
	 *
	 * @return will never be {@literal null}.
	 */
	Iterable<EventPublication> findIncompletePublications();
}
