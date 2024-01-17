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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.util.Assert;

/**
 * Default {@link Completable} implementation.
 *
 * @author Oliver Drotbohm
 */
class DefaultEventPublication implements EventPublication {

	private final UUID identifier;
	private final Object event;
	private final PublicationTargetIdentifier targetIdentifier;
	private final Instant publicationDate;

	private Optional<Instant> completionDate;

	/**
	 * Creates a new {@link DefaultEventPublication} for the given event and {@link PublicationTargetIdentifier}.
	 *
	 * @param event must not be {@literal null}.
	 * @param targetIdentifier must not be {@literal null}.
	 * @param publicationDate must not be {@literal null}.
	 */
	DefaultEventPublication(Object event, PublicationTargetIdentifier targetIdentifier, Instant publicationDate) {

		Assert.notNull(event, "Event must not be null!");
		Assert.notNull(targetIdentifier, "PublicationTargetIdentifier must not be null!");
		Assert.notNull(publicationDate, "Publication date must not be null!");

		this.identifier = UUID.randomUUID();
		this.event = event;
		this.targetIdentifier = targetIdentifier;
		this.publicationDate = publicationDate;
		this.completionDate = Optional.empty();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublication#getPublicationIdentifier()
	 */
	@Override
	public UUID getIdentifier() {
		return identifier;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublication#getEvent()
	 */
	@Override
	public Object getEvent() {
		return event;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublication#getTargetIdentifier()
	 */
	@Override
	public PublicationTargetIdentifier getTargetIdentifier() {
		return targetIdentifier;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublication#getPublicationDate()
	 */
	public Instant getPublicationDate() {
		return publicationDate;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.CompletableEventPublication#getCompletionDate()
	 */
	public Optional<Instant> getCompletionDate() {
		return completionDate;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.CompletableEventPublication#markCompleted(java.time.Instant)
	 */
	@Override
	public void markCompleted(Instant instant) {
		this.completionDate = Optional.of(instant);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return "DefaultEventPublication [event=" + event + ", targetIdentifier=" + targetIdentifier + ", publicationDate="
				+ publicationDate + ", completionDate=" + completionDate + "]";
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof DefaultEventPublication that)) {
			return false;
		}

		return Objects.equals(this.completionDate, that.completionDate) //
				&& Objects.equals(this.event, that.event) //
				&& Objects.equals(this.publicationDate, that.publicationDate) //
				&& Objects.equals(this.targetIdentifier, that.targetIdentifier);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(completionDate, event, publicationDate, targetIdentifier);
	}
}
