/*
 * Copyright 2017-2024 the original author or authors.
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.util.Assert;

/**
 * An event publication.
 *
 * @author Oliver Drotbohm
 * @author Björn Kieling
 * @author Dmitry Belyaev
 */
public interface EventPublication extends Comparable<EventPublication>, Completable {

	/**
	 * Creates a {@link EventPublication} for the given event an listener identifier using a default {@link Instant}.
	 * Prefer using {@link #of(Object, PublicationTargetIdentifier, Instant)} with a dedicated {@link Instant} obtained
	 * from a {@link Clock}.
	 *
	 * @param event must not be {@literal null}.
	 * @param id must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @see #of(Object, PublicationTargetIdentifier, Instant)
	 */
	static EventPublication of(Object event, PublicationTargetIdentifier id) {
		return new DefaultEventPublication(event, id, Instant.now());
	}

	/**
	 * Creates a {@link EventPublication} for the given event an listener identifier and publication date.
	 *
	 * @param event must not be {@literal null}.
	 * @param id must not be {@literal null}.
	 * @param publicationDate must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static EventPublication of(Object event, PublicationTargetIdentifier id, Instant publicationDate) {
		return new DefaultEventPublication(event, id, publicationDate);
	}

	/**
	 * Returns a unique identifier for this publication.
	 *
	 * @return will never be {@literal null}.
	 */
	UUID getIdentifier();

	/**
	 * Returns the event that is published.
	 *
	 * @return
	 */
	Object getEvent();

	/**
	 * Returns the event as Spring {@link ApplicationEvent}, effectively wrapping it into a
	 * {@link PayloadApplicationEvent} in case it's not one already.
	 *
	 * @return
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
	 * @return
	 */
	Instant getPublicationDate();

	/**
	 * Returns the identifier of the target that the event is supposed to be published to.
	 *
	 * @return
	 */
	PublicationTargetIdentifier getTargetIdentifier();

	/**
	 * Returns whether the publication is identified by the given {@link PublicationTargetIdentifier}.
	 *
	 * @param identifier must not be {@literal null}.
	 * @return
	 */
	default boolean isIdentifiedBy(PublicationTargetIdentifier identifier) {

		Assert.notNull(identifier, "Identifier must not be null!");

		return this.getTargetIdentifier().equals(identifier);
	}

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
	@Override
	public default int compareTo(EventPublication that) {
		return this.getPublicationDate().compareTo(that.getPublicationDate());
	}
}
