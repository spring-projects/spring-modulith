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
			SELECT *
			FROM EVENT_PUBLICATION
			WHERE
					SERIALIZED_EVENT = ?
					AND LISTENER_ID = ?
					AND COMPLETION_DATE IS NULL
			ORDER BY PUBLICATION_DATE
			""";

	private static final String SQL_STATEMENT_DELETE_UNCOMPLETED = """
					DELETE
					FROM EVENT_PUBLICATION
					WHERE
							COMPLETION_DATE IS NOT NULL
			""";

	private final JdbcOperations operations;
	private final EventSerializer serializer;
	private final DatabaseType databaseType;

	@Override
	@Transactional
	public EventPublication create(EventPublication publication) {

		var serializedEvent = serializeEvent(publication.getEvent());

		operations.update( //
				SQL_STATEMENT_INSERT, //
				uuidToDatabase(UUID.randomUUID()), //
				publication.getEvent().getClass().getName(), //
				publication.getTargetIdentifier().getValue(), //
				Timestamp.from(publication.getPublicationDate()), //
				serializedEvent);

		return publication;
	}

	@Override
	@Transactional
	public EventPublication update(CompletableEventPublication publication) {

		var serializedEvent = serializeEvent(publication.getEvent());
		var listenerId = publication.getTargetIdentifier().getValue();
		var potentialPublicationIdsToBeUpdated = operations.query( //
				SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID, //
				(rs, rowNum) -> getUuidFromResultSet(rs), //
				serializedEvent, //
				listenerId);

		potentialPublicationIdsToBeUpdated.stream()
				.findFirst()
				.ifPresent(id -> update(id, publication));

		return publication;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventPublication> findIncompletePublicationsByEventAndTargetIdentifier( //
			Object event, PublicationTargetIdentifier targetIdentifier) {

		var serializedEvent = serializeEvent(event);
		var listenerId = targetIdentifier.getValue();

		return findAllIncompletePublicationsByEventAndListenerId(serializedEvent, listenerId).stream() //
				.findFirst();
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("null")
	public List<EventPublication> findIncompletePublications() {

		return operations.query( //
				SQL_STATEMENT_FIND_UNCOMPLETED, //
				this::resultSetToPublications);
	}

	@Override
	public void deleteCompletedPublications() {
		operations.execute(SQL_STATEMENT_DELETE_UNCOMPLETED);
	}

	private void update(UUID id, CompletableEventPublication publication) {

		var timestamp = publication.getCompletionDate().map(Timestamp::from).orElse(null);

		operations.update( //
				SQL_STATEMENT_UPDATE, //
				timestamp, //
				uuidToDatabase(id));
	}

	@SuppressWarnings("null")
	private List<EventPublication> findAllIncompletePublicationsByEventAndListenerId(
			String serializedEvent, String listenerId) {

		return operations.query( //
				SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID, //
				this::resultSetToPublications, //
				serializedEvent, //
				listenerId);
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

			var publication = resultSetToPublication(resultSet);

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

		var id = getUuidFromResultSet(rs);
		var eventClass = loadClass(id, rs.getString("EVENT_TYPE"));

		if (eventClass == null) {
			return null;
		}

		var completionDate = rs.getTimestamp("COMPLETION_DATE");

		return JdbcEventPublication.builder().completionDate(completionDate == null ? null : completionDate.toInstant())
				.eventType(eventClass) //
				.listenerId(rs.getString("LISTENER_ID")) //
				.publicationDate(rs.getTimestamp("PUBLICATION_DATE").toInstant()) //
				.serializedEvent(rs.getString("SERIALIZED_EVENT")) //
				.serializer(serializer) //
				.build();
	}

	private Object uuidToDatabase(UUID id) {
		return databaseType.uuidToDatabase(id);
	}

	private UUID getUuidFromResultSet(ResultSet rs) throws SQLException {
		return databaseType.databaseToUUID(rs.getObject("ID"));
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
