/*
 * Copyright 2023-2024 the original author or authors.
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;

/**
 * An event publication.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
public interface EventPublication {

	/**
	 * Returns a unique identifier for this publication.
	 *
	 * @return will never be {@literal null}.
	 */
	UUID getIdentifier();

	/**
	 * Returns the event that is published.
	 *
	 * @return will never be {@literal null}.
	 */
	Object getEvent();

	/**
	 * Returns the event as Spring {@link ApplicationEvent}, effectively wrapping it into a
	 * {@link PayloadApplicationEvent} in case it's not one already.
	 *
	 * @return the underlying event as {@link ApplicationEvent}.
	 */
	default ApplicationEvent getApplicationEvent() {

		Object event = getEvent();

		return PayloadApplicationEvent.class.isInstance(event) //
				? PayloadApplicationEvent.class.cast(event)
				: new PayloadApplicationEvent<>(this, event);
	}

	/**
	 * Returns the time the event is published at.
	 *
	 * @return will never be {@literal null}.
	 */
	Instant getPublicationDate();

	/**
	 * Returns the completion date of the publication.
	 *
	 * @return will never be {@literal null}.
	 */
	Optional<Instant> getCompletionDate();

	/**
	 * Returns whether the publication of the event has completed.
	 *
	 * @return will never be {@literal null}.
	 */
	default boolean isPublicationCompleted() {
		return getCompletionDate().isPresent();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@SuppressWarnings("javadoc")
	default int compareTo(EventPublication that) {
		return this.getPublicationDate().compareTo(that.getPublicationDate());
	}
}
