/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.modulith.events.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.annotation.Transactional;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Repository to store {@link EventPublication}s.
 *
 * @author Dmitry Belyaev, Björn Kieling
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcEventPublicationRepository implements EventPublicationRepository {

	private static final String SQL_STATEMENT_INSERT = """
			INSERT INTO EVENT_PUBLICATION (ID, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT)
			VALUES (?, ?, ?, ?, ?)
			""";
	private static final String SQL_STATEMENT_FIND_UNCOMPLETED = """
			SELECT ID, COMPLETION_DATE, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT
			FROM EVENT_PUBLICATION
			WHERE COMPLETION_DATE IS NULL
			""";
	private static final String SQL_STATEMENT_UPDATE = """
			UPDATE EVENT_PUBLICATION
			SET COMPLETION_DATE = ?
			WHERE ID = ?
			""";
	private static final String SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID = """
			SELECT * FROM EVENT_PUBLICATION
			WHERE SERIALIZED_EVENT = ? AND LISTENER_ID = ?
			ORDER BY PUBLICATION_DATE
			""";

	private final JdbcTemplate jdbcTemplate;
	private final EventSerializer serializer;

	@Override
	@Transactional
	public EventPublication create(EventPublication publication) {
		String serializedEvent = serializeEvent(publication.getEvent());
		jdbcTemplate.update(SQL_STATEMENT_INSERT, //
				UUID.randomUUID(), //
				publication.getEvent().getClass().getName(), //
				publication.getTargetIdentifier().getValue(), //
				publication.getPublicationDate(), //
				serializedEvent);

		return publication;
	}

	@Override
	@Transactional
	public EventPublication updateCompletionDate(CompletableEventPublication publication) {
		String serializedEvent = serializeEvent(publication.getEvent());
		List<UUID> results = jdbcTemplate.query(SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID,
				(rs, rowNum) -> rs.getObject("ID", UUID.class), serializedEvent, publication.getTargetIdentifier().getValue());
		if (!results.isEmpty()) {
			jdbcTemplate.update(SQL_STATEMENT_UPDATE, publication.getCompletionDate().orElse(null), results.get(0));
		}

		return publication;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EventPublication> findByCompletionDateIsNull() {
		return jdbcTemplate.query(SQL_STATEMENT_FIND_UNCOMPLETED, this::mapResultSetToEventPublications);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventPublication> findByEventAndTargetIdentifier(Object event,
			PublicationTargetIdentifier targetIdentifier) {

		String serializedEvent = serializeEvent(event);
		List<EventPublication> results = jdbcTemplate.query(SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID,
				this::mapResultSetToEventPublications, serializedEvent, targetIdentifier.getValue());
		if (results.isEmpty()) {
			return Optional.empty();
		} else {
			// if there are several events with exactly the same payload we return the oldest one first
			return Optional.of(results.get(0));
		}
	}

	private String serializeEvent(Object event) {
		return serializer.serialize(event).toString();
	}

	private List<EventPublication> mapResultSetToEventPublications(ResultSet rs) throws SQLException {
		var result = new ArrayList<EventPublication>();
		while (rs.next()) {
			entityToDomain(rs).ifPresent(result::add);
		}
		return result;
	}

	private Optional<EventPublication> entityToDomain(ResultSet rs) throws SQLException {
		var id = rs.getObject("ID", UUID.class);
		var eventClassName = rs.getString("EVENT_TYPE");
		Class<?> eventClass;
		try {
			eventClass = Class.forName(eventClassName);
		} catch (ClassNotFoundException e) {
			LOG.warn("Event '{}' of unknown type '{}' found", id, eventClassName);
			return Optional.empty();
		}

		return Optional.of(JdbcEventPublication.builder()
				.completionDate(Optional.ofNullable(rs.getTimestamp("COMPLETION_DATE")).map(Timestamp::toInstant).orElse(null))
				.eventType(eventClass) //
				.listenerId(rs.getString("LISTENER_ID")) //
				.publicationDate(rs.getTimestamp("PUBLICATION_DATE").toInstant()) //
				.serializedEvent(rs.getString("SERIALIZED_EVENT")) //
				.serializer(serializer) //
				.build());
	}

	@EqualsAndHashCode
	@Builder
	private static class JdbcEventPublication implements CompletableEventPublication {

		private final UUID id;
		private final Instant publicationDate;
		private final String listenerId;
		private final String serializedEvent;
		private final Class<?> eventType;

		private final EventSerializer serializer;
		private Instant completionDate;

		@Override
		public Object getEvent() {
			return serializer.deserialize(serializedEvent, eventType);
		}

		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(listenerId);
		}

		@Override
		public Instant getPublicationDate() {
			return publicationDate;
		}

		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(completionDate);
		}

		@Override
		public boolean isPublicationCompleted() {
			return completionDate != null;
		}

		@Override
		public CompletableEventPublication markCompleted() {
			this.completionDate = Instant.now();
			return this;
		}
	}
}
