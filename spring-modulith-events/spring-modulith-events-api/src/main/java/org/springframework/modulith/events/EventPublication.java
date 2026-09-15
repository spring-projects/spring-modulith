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
package org.springframework.modulith.events;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
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
	 * @since 1.3
	 */
	default boolean isCompleted() {
		return getCompletionDate().isPresent();
	}

	/**
	 * Return the publication's {@link Status}.
	 *
	 * @return will never be {@literal null}.
	 * @since 2.0
	 */
	Status getStatus();

	/**
	 * Returns the last time the {@link EventPublication} was resubmitted.
	 *
	 * @return can be {@literal null}.
	 * @since 2.0
	 */
	@Nullable
	Instant getLastResubmissionDate();

	/**
	 * Returns the date that the publication has been last published at. This will consider the most recent resubmission
	 * before falling back to the original publication date.
	 *
	 * @return will never be {@literal null}.
	 * @since 2.2, 2.1.2, 2.0.9
	 * @see #getLastResubmissionDate()
	 * @see #getPublicationDate()
	 */
	default Instant getLastPublicationDate() {

		var lastResubmissionDate = getLastResubmissionDate();

		return lastResubmissionDate != null ? lastResubmissionDate : getPublicationDate();
	}

	/**
	 * Returns the number of completion attempts.
	 *
	 * @since 2.0
	 */
	int getCompletionAttempts();

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	default int compareTo(EventPublication that) {
		return this.getPublicationDate().compareTo(that.getPublicationDate());
	}

	/**
	 * The status of an {@link EventPublication}.
	 *
	 * @author Oliver Drotbohm
	 * @since 2.0
	 */
	enum Status {

		/**
		 * The event publication has been published initially.
		 */
		PUBLISHED,

		/**
		 * An event listener has picked up the publication and is processing it.
		 */
		PROCESSING,

		/**
		 * The processing of the publication has successfully completed.
		 */
		COMPLETED,

		/**
		 * The processing ended up in a failure.
		 */
		FAILED,

		/**
		 * A previously failed publication has been resubmitted for processing.
		 */
		RESUBMITTED,

		/**
		 * The publication has permanently given up on further resubmission attempts, as decided by an application-defined
		 * policy. Distinct from {@link #COMPLETED}, which indicates a successful listener invocation.
		 *
		 * @since 2.2
		 */
		ABANDONED;

		/**
		 * Returns whether this {@link Status} is terminal, i.e. the publication will not transition into any other
		 * {@link Status} anymore.
		 *
		 * @since 2.2
		 */
		public boolean isTerminal() {
			return this == COMPLETED || this == ABANDONED;
		}
	}
}
