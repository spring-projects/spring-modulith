/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.test;

import org.springframework.modulith.test.PublishedEvents.TypedPublishedEvents;
import org.springframework.util.Assert;

/**
 * @author Oliver Drotbohm
 */
interface TypedEvents {

	/**
	 * Returns all events of the given type.
	 *
	 * @param <T> the event type
	 * @param type must not be {@literal null}.
	 * @return
	 */
	<T> TypedPublishedEvents<T> ofType(Class<T> type);

	/**
	 * Returns whether an event of the given type was published.
	 *
	 * @param type must not be {@literal null}.
	 * @return whether an event of the given type was published.
	 */
	default boolean eventOfTypeWasPublished(Class<?> type) {

		Assert.notNull(type, "Event type must not be null!");

		return ofType(type).iterator().hasNext();
	}
}
