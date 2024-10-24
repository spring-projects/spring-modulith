package org.springframework.modulith.events.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity to represent archived event publications.
 *
 * @author Oliver Drotbohm
 */
@Entity
@Table(name = "EVENT_PUBLICATION_ARCHIVE")
class ArchivedJpaEventPublication extends JpaEventPublication {

	/**
	 * Creates a new {@link ArchivedJpaEventPublication} for the given publication date, listener id, serialized event and
	 * event type.
	 *
	 * @param id
	 * @param publicationDate must not be {@literal null}.
	 * @param listenerId must not be {@literal null} or empty.
	 * @param serializedEvent must not be {@literal null} or empty.
	 * @param eventType must not be {@literal null}.
	 */
	public ArchivedJpaEventPublication(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
			Class<?> eventType) {
		super(id, publicationDate, listenerId, serializedEvent, eventType);
	}

	public ArchivedJpaEventPublication() {}
}
