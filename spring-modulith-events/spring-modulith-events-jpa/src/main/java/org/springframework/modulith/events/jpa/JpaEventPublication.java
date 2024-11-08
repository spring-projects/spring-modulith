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
package org.springframework.modulith.events.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.util.Assert;

/**
 * JPA entity to represent event publications.
 *
 * @author Oliver Drotbohm
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Cora Iberkleid
 */
@MappedSuperclass
abstract class JpaEventPublication {

	final @Id @Column(length = 16) UUID id;
	final Instant publicationDate;
	final String listenerId;
	final String serializedEvent;
	final Class<?> eventType;

	Instant completionDate;

	/**
	 * Creates a new {@link JpaEventPublication} for the given publication date, listener id, serialized event and event
	 * type.
	 *
	 * @param publicationDate must not be {@literal null}.
	 * @param listenerId must not be {@literal null} or empty.
	 * @param serializedEvent must not be {@literal null} or empty.
	 * @param eventType must not be {@literal null}.
	 */
	private JpaEventPublication(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
			Class<?> eventType) {

		Assert.notNull(id, "Identifier must not be null!");
		Assert.notNull(publicationDate, "Publication date must not be null!");
		Assert.notNull(listenerId, "Listener id must not be null or empty!");
		Assert.notNull(serializedEvent, "Serialized event must not be null or empty!");
		Assert.notNull(eventType, "Event type must not be null!");

		this.id = id;
		this.publicationDate = publicationDate;
		this.listenerId = listenerId;
		this.serializedEvent = serializedEvent;
		this.eventType = eventType;
	}

	protected JpaEventPublication() {

		this.id = null;
		this.publicationDate = null;
		this.listenerId = null;
		this.serializedEvent = null;
		this.eventType = null;
	}

	static JpaEventPublication of(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
			Class<?> eventType) {
		return new DefaultJpaEventPublication(id, publicationDate, listenerId, serializedEvent, eventType);
	}

	static Class<? extends JpaEventPublication> getIncompleteType() {
		return DefaultJpaEventPublication.class;
	}

	static Class<? extends JpaEventPublication> getCompletedType(CompletionMode mode) {
		return mode == CompletionMode.ARCHIVE ? ArchivedJpaEventPublication.class : DefaultJpaEventPublication.class;
	}

	ArchivedJpaEventPublication archive(Instant instant) {

		var result = new ArchivedJpaEventPublication(id, publicationDate, listenerId, serializedEvent, eventType);
		result.completionDate = instant;

		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (obj == this) {
			return true;
		}

		if (!(obj instanceof JpaEventPublication that)) {
			return false;
		}

		return Objects.equals(this.id, that.id);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Entity(name = "DefaultJpaEventPublication")
	@Table(name = "EVENT_PUBLICATION")
	private static class DefaultJpaEventPublication extends JpaEventPublication {

		private DefaultJpaEventPublication(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
				Class<?> eventType) {
			super(id, publicationDate, listenerId, serializedEvent, eventType);
		}

		@SuppressWarnings("unused")
		DefaultJpaEventPublication() {}
	}

	@Entity(name = "ArchivedJpaEventPublication")
	@Table(name = "EVENT_PUBLICATION_ARCHIVE")
	private static class ArchivedJpaEventPublication extends JpaEventPublication {

		private ArchivedJpaEventPublication(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
				Class<?> eventType) {
			super(id, publicationDate, listenerId, serializedEvent, eventType);
		}

		@SuppressWarnings("unused")
		ArchivedJpaEventPublication() {}
	}
}
