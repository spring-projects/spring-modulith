/*
 * Copyright 2022-2023 the original author or authors.
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublicationRepository;
import org.springframework.modulith.events.EventSerializer;
import org.springframework.modulith.events.PublicationTargetIdentifier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JDBC-based repository to store {@link EventPublication}s.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
class JdbcEventPublicationRepository implements EventPublicationRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcEventPublicationRepository.class);

	private static final String SQL_STATEMENT_INSERT = """
			INSERT INTO EVENT_PUBLICATION (ID, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT)
			VALUES (?, ?, ?, ?, ?)
			""";

	private static final String SQL_STATEMENT_FIND_UNCOMPLETED = """
			SELECT ID, COMPLETION_DATE, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT
			FROM EVENT_PUBLICATION
			WHERE COMPLETION_DATE IS NULL
			ORDER BY PUBLICATION_DATE ASC
			""";

	private static final String SQL_STATEMENT_UPDATE = """
			UPDATE EVENT_PUBLICATION
			SET COMPLETION_DATE = ?
			WHERE ID = ?
			""";

	private static final String SQL_STATEMENT_UPDATE_BY_EVENT_AND_LISTENER_ID = """
			UPDATE EVENT_PUBLICATION
			SET COMPLETION_DATE = ?
			WHERE
					LISTENER_ID = ?
					AND SERIALIZED_EVENT = ?
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

	private static final String SQL_STATEMENT_DELETE_UNCOMPLETED_BEFORE = """
			DELETE
			FROM EVENT_PUBLICATION
			WHERE
					COMPLETION_DATE < ?
			""";

	private final JdbcOperations operations;
	private final EventSerializer serializer;
	private final DatabaseType databaseType;

	/**
	 * Creates a new {@link JdbcEventPublicationRepository} for the given {@link JdbcOperations}, {@link EventSerializer}
	 * and {@link DatabaseType}.
	 *
	 * @param operations must not be {@literal null}.
	 * @param serializer must not be {@literal null}.
	 * @param databaseType must not be {@literal null}.
	 */
	public JdbcEventPublicationRepository(JdbcOperations operations, EventSerializer serializer,
			DatabaseType databaseType) {

		Assert.notNull(operations, "JdbcOperations must not be null!");
		Assert.notNull(serializer, "EventSerializer must not be null!");
		Assert.notNull(databaseType, "DatabaseType must not be null!");

		this.operations = operations;
		this.serializer = serializer;
		this.databaseType = databaseType;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#create(org.springframework.modulith.events.EventPublication)
	 */
	@Override
	@Transactional
	public EventPublication create(EventPublication publication) {

		var serializedEvent = serializeEvent(publication.getEvent());

		operations.update( //
				SQL_STATEMENT_INSERT, //
				uuidToDatabase(publication.getIdentifier()), //
				publication.getEvent().getClass().getName(), //
				publication.getTargetIdentifier().getValue(), //
				Timestamp.from(publication.getPublicationDate()), //
				serializedEvent);

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	@Transactional
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		operations.update(SQL_STATEMENT_UPDATE_BY_EVENT_AND_LISTENER_ID, //
				Timestamp.from(completionDate), //
				identifier.getValue(), //
				serializer.serialize(event));
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
		return operations.query(SQL_STATEMENT_FIND_UNCOMPLETED, this::resultSetToPublications);
	}

	@Override
	public void deleteCompletedPublications() {
		operations.execute(SQL_STATEMENT_DELETE_UNCOMPLETED);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		operations.update(SQL_STATEMENT_DELETE_UNCOMPLETED_BEFORE, Timestamp.from(instant));
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
		var publicationDate = rs.getTimestamp("PUBLICATION_DATE").toInstant();
		var listenerId = rs.getString("LISTENER_ID");
		var serializedEvent = rs.getString("SERIALIZED_EVENT");

		return new JdbcEventPublication(id, publicationDate, listenerId, serializedEvent, eventClass, serializer,
				completionDate == null ? null : completionDate.toInstant());
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
			LOGGER.warn("Event '{}' of unknown type '{}' found", id, className);
			return null;
		}
	}

	private static class JdbcEventPublication implements EventPublication {

		private final UUID id;
		private final Instant publicationDate;
		private final String listenerId;
		private final String serializedEvent;
		private final Class<?> eventType;

		private final EventSerializer serializer;
		private @Nullable Instant completionDate;

		/**
		 * @param id must not be {@literal null}.
		 * @param publicationDate must not be {@literal null}.
		 * @param listenerId must not be {@literal null} or empty.
		 * @param serializedEvent must not be {@literal null} or empty.
		 * @param eventType must not be {@literal null}.
		 * @param serializer must not be {@literal null}.
		 * @param completionDate can be {@literal null}.
		 */
		public JdbcEventPublication(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
				Class<?> eventType, EventSerializer serializer, @Nullable Instant completionDate) {

			Assert.notNull(id, "Id must not be null!");
			Assert.notNull(publicationDate, "Publication date must not be null!");
			Assert.hasText(listenerId, "Listener id must not be null or empty!");
			Assert.hasText(serializedEvent, "Serialized event must not be null or empty!");
			Assert.notNull(eventType, "Event type must not be null!");
			Assert.notNull(serializer, "EventSerializer must not be null!");

			this.id = id;
			this.publicationDate = publicationDate;
			this.listenerId = listenerId;
			this.serializedEvent = serializedEvent;
			this.eventType = eventType;
			this.serializer = serializer;
			this.completionDate = completionDate;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getPublicationIdentifier()
		 */
		@Override
		public UUID getIdentifier() {
			return id;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getEvent()
		 */
		@Override
		public Object getEvent() {
			return serializer.deserialize(serializedEvent, eventType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getTargetIdentifier()
		 */
		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(listenerId);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.EventPublication#getPublicationDate()
		 */
		@Override
		public Instant getPublicationDate() {
			return publicationDate;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.CompletableEventPublication#getCompletionDate()
		 */
		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(completionDate);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.CompletableEventPublication#isPublicationCompleted()
		 */
		@Override
		public boolean isPublicationCompleted() {
			return completionDate != null;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.events.Completable#markCompleted(java.time.Instant)
		 */
		@Override
		public void markCompleted(Instant instant) {
			this.completionDate = instant;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(@Nullable Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof JdbcEventPublication that)) {
				return false;
			}

			return Objects.equals(completionDate, that.completionDate) //
					&& Objects.equals(eventType, that.eventType) //
					&& Objects.equals(id, that.id) //
					&& Objects.equals(listenerId, that.listenerId) //
					&& Objects.equals(publicationDate, that.publicationDate) //
					&& Objects.equals(serializedEvent, that.serializedEvent) //
					&& Objects.equals(serializer, that.serializer);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(completionDate, eventType, id, listenerId, publicationDate, serializedEvent, serializer);
		}
	}
}
