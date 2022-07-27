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

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.CompletableEventPublication;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-based repository to store {@link EventPublication}s.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
@Slf4j
@RequiredArgsConstructor
class JdbcEventPublicationRepository implements EventPublicationRepository {

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

	private final JdbcOperations operations;
	private final EventSerializer serializer;

	@Override
	@Transactional
	public EventPublication create(EventPublication publication) {

		operations.update(SQL_STATEMENT_INSERT, //
				UUID.randomUUID(), //
				publication.getEvent().getClass().getName(), //
				publication.getTargetIdentifier().getValue(), //
				Timestamp.from(publication.getPublicationDate()), //
				serializeEvent(publication.getEvent()));

		return publication;
	}

	@Override
	@Transactional
	public EventPublication update(CompletableEventPublication publication) {

		var serializedEvent = serializeEvent(publication.getEvent());
		var results = operations.query(SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID,
				(rs, rowNum) -> rs.getObject("ID", UUID.class), serializedEvent, publication.getTargetIdentifier().getValue());

		if (!results.isEmpty()) {

			operations.update( //
					SQL_STATEMENT_UPDATE, //
					publication.getCompletionDate().map(Timestamp::from).orElse(null), //
					results.get(0));
		}

		return publication;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventPublication> findByEventAndTargetIdentifier(Object event,
			PublicationTargetIdentifier targetIdentifier) {

		var results = operations.query(SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID, this::resultSetToPublications,
				serializeEvent(event), targetIdentifier.getValue());

		return Optional.ofNullable(results == null || results.isEmpty() ? null : results.get(0));
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("null")
	public List<EventPublication> findIncompletePublications() {
		return operations.query(SQL_STATEMENT_FIND_UNCOMPLETED, this::resultSetToPublications);
	}

	private String serializeEvent(Object event) {
		return serializer.serialize(event).toString();
	}

	/**
	 * Effectively a {@link ResultSetExtractor} to drop {@link EventPublication}s that cannot be deserialized.
	 *
	 * @param resultSet must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @throws SQLException
	 */
	private List<EventPublication> resultSetToPublications(ResultSet resultSet) throws SQLException {

		List<EventPublication> result = new ArrayList<>();

		while (resultSet.next()) {

			EventPublication publication = resultSetToPublication(resultSet);

			if (publication != null) {
				result.add(publication);
			}
		}

		return result;
	}

	/**
	 * Effectively a {@link RowMapper} to turn a single row into an {@link EventPublication}.
	 *
	 * @param rs must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @throws SQLException
	 */
	@Nullable
	private EventPublication resultSetToPublication(ResultSet rs) throws SQLException {

		var id = rs.getObject("ID", UUID.class);
		var eventClass = loadClass(id, rs.getString("EVENT_TYPE"));

		if (eventClass == null) {
			return null;
		}

		var completionDate = rs.getTimestamp("COMPLETION_DATE");

		return JdbcEventPublication.builder()
				.completionDate(completionDate == null ? null : completionDate.toInstant())
				.eventType(eventClass) //
				.listenerId(rs.getString("LISTENER_ID")) //
				.publicationDate(rs.getTimestamp("PUBLICATION_DATE").toInstant()) //
				.serializedEvent(rs.getString("SERIALIZED_EVENT")) //
				.serializer(serializer) //
				.build();
	}

	@Nullable
	private Class<?> loadClass(UUID id, String className) {

		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			LOG.warn("Event '{}' of unknown type '{}' found", id, className);
			return null;
		}
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
		private @Nullable Instant completionDate;

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
