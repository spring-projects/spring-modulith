/*
 * Copyright 2022-2024 the original author or authors.
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * JDBC-based repository to store {@link TargetEventPublication}s.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 * @author Raed Ben Hamouda
 * @author Cora Iberkleid
 */
class JdbcEventPublicationRepository implements EventPublicationRepository, BeanClassLoaderAware {

	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcEventPublicationRepository.class);

	private static final String SQL_STATEMENT_INSERT = """
			INSERT INTO %s (ID, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT)
			VALUES (?, ?, ?, ?, ?)
			""";

	private static final String SQL_STATEMENT_FIND_COMPLETED = """
			SELECT ID, COMPLETION_DATE, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT
			FROM %s
			WHERE COMPLETION_DATE IS NOT NULL
			ORDER BY PUBLICATION_DATE ASC
			""";

	private static final String SQL_STATEMENT_FIND_UNCOMPLETED = """
			SELECT ID, COMPLETION_DATE, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT
			FROM %s
			WHERE COMPLETION_DATE IS NULL
			ORDER BY PUBLICATION_DATE ASC
			""";

	private static final String SQL_STATEMENT_FIND_UNCOMPLETED_BEFORE = """
			SELECT ID, COMPLETION_DATE, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT
			FROM %s
			WHERE
					COMPLETION_DATE IS NULL
					AND PUBLICATION_DATE < ?
			ORDER BY PUBLICATION_DATE ASC
			""";

	private static final String SQL_STATEMENT_UPDATE_BY_EVENT_AND_LISTENER_ID = """
			UPDATE %s
			SET COMPLETION_DATE = ?
			WHERE
					LISTENER_ID = ?
					AND COMPLETION_DATE IS NULL
					AND SERIALIZED_EVENT = ?
			""";

	private static final String SQL_STATEMENT_UPDATE_BY_ID = """
			UPDATE %s
			SET COMPLETION_DATE = ?
			WHERE
					ID = ?
			""";

	private static final String SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID = """
			SELECT *
			FROM %s
			WHERE
					SERIALIZED_EVENT = ?
					AND LISTENER_ID = ?
					AND COMPLETION_DATE IS NULL
			ORDER BY PUBLICATION_DATE
			""";

	private static final String SQL_STATEMENT_DELETE = """
			DELETE
			FROM %s
			WHERE
					ID IN
			""";

	private static final String SQL_STATEMENT_DELETE_BY_EVENT_AND_LISTENER_ID = """
			DELETE FROM %s
			WHERE
					LISTENER_ID = ?
					AND SERIALIZED_EVENT = ?
			""";

	private static final String SQL_STATEMENT_DELETE_BY_ID = """
			DELETE
			FROM %s
			WHERE
					ID = ?
			""";

	private static final String SQL_STATEMENT_DELETE_COMPLETED = """
			DELETE
			FROM %s
			WHERE
					COMPLETION_DATE IS NOT NULL
			""";

	private static final String SQL_STATEMENT_DELETE_COMPLETED_BEFORE = """
			DELETE
			FROM %s
			WHERE
					COMPLETION_DATE < ?
			""";

	private static final String SQL_STATEMENT_COPY_TO_ARCHIVE_BY_ID = """
			-- Only copy if no entry in target table
			INSERT INTO %s (ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, COMPLETION_DATE)
			SELECT ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, ?
			 	FROM %s
			 	WHERE ID = ? 
			 	  AND NOT EXISTS (SELECT 1 FROM %s WHERE ID = EVENT_PUBLICATION.ID)
			""";

	private static final String SQL_STATEMENT_COPY_TO_ARCHIVE_BY_EVENT_AND_LISTENER_ID = """
			-- Only copy if no entry in target table
			INSERT INTO %s (ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, COMPLETION_DATE)
			SELECT ID, LISTENER_ID, EVENT_TYPE, SERIALIZED_EVENT, PUBLICATION_DATE, ?
			 	FROM %s
			 	WHERE LISTENER_ID = ?
				  AND SERIALIZED_EVENT = ?
				  AND NOT EXISTS (SELECT 1 FROM %s WHERE ID = EVENT_PUBLICATION.ID) 
			""";

	private static final int DELETE_BATCH_SIZE = 100;

	private final JdbcOperations operations;
	private final EventSerializer serializer;
	private final JdbcRepositorySettings settings;

	private ClassLoader classLoader;

	private final String sqlStatementInsert,
			sqlStatementFindCompleted,
			sqlStatementFindUncompleted,
			sqlStatementFindUncompletedBefore,
			sqlStatementUpdateByEventAndListenerId,
			sqlStatementUpdateById,
			sqlStatementFindByEventAndListenerId,
			sqlStatementDelete,
			sqlStatementDeleteByEventAndListenerId,
			sqlStatementDeleteById,
			sqlStatementDeleteCompleted,
			sqlStatementDeleteCompletedBefore,
			sqlStatementCopyToArchive,
			sqlStatementCopyToArchiveByEventAndListenerId;

	/**
	 * Creates a new {@link JdbcEventPublicationRepository} for the given {@link JdbcOperations}, {@link EventSerializer},
	 * {@link DatabaseType} and {@link JdbcConfigurationProperties}.
	 *
	 * @param operations must not be {@literal null}.
	 * @param serializer must not be {@literal null}.
	 * @param settings must not be {@literal null}.
	 */
	public JdbcEventPublicationRepository(JdbcOperations operations, EventSerializer serializer,
			JdbcRepositorySettings settings) {

		Assert.notNull(operations, "JdbcOperations must not be null!");
		Assert.notNull(serializer, "EventSerializer must not be null!");
		Assert.notNull(settings, "DatabaseType must not be null!");

		this.operations = operations;
		this.serializer = serializer;
		this.settings = settings;

		var schema = settings.getSchema();
		var table = ObjectUtils.isEmpty(schema) ? "EVENT_PUBLICATION" : schema + ".EVENT_PUBLICATION";
		var completedTable = settings.isArchiveCompletion() ? table + "_ARCHIVE" : table;

		this.sqlStatementInsert = SQL_STATEMENT_INSERT.formatted(table);
		this.sqlStatementFindCompleted = SQL_STATEMENT_FIND_COMPLETED.formatted(completedTable);
		this.sqlStatementFindUncompleted = SQL_STATEMENT_FIND_UNCOMPLETED.formatted(table);
		this.sqlStatementFindUncompletedBefore = SQL_STATEMENT_FIND_UNCOMPLETED_BEFORE.formatted(table);
		this.sqlStatementUpdateByEventAndListenerId = SQL_STATEMENT_UPDATE_BY_EVENT_AND_LISTENER_ID.formatted(table);
		this.sqlStatementUpdateById = SQL_STATEMENT_UPDATE_BY_ID.formatted(table);
		this.sqlStatementFindByEventAndListenerId = SQL_STATEMENT_FIND_BY_EVENT_AND_LISTENER_ID.formatted(table);
		this.sqlStatementDelete = SQL_STATEMENT_DELETE.formatted(table);
		this.sqlStatementDeleteByEventAndListenerId = SQL_STATEMENT_DELETE_BY_EVENT_AND_LISTENER_ID.formatted(table);
		this.sqlStatementDeleteById = SQL_STATEMENT_DELETE_BY_ID.formatted(table);
		this.sqlStatementDeleteCompleted = SQL_STATEMENT_DELETE_COMPLETED.formatted(completedTable);
		this.sqlStatementDeleteCompletedBefore = SQL_STATEMENT_DELETE_COMPLETED_BEFORE.formatted(completedTable);
		this.sqlStatementCopyToArchive = SQL_STATEMENT_COPY_TO_ARCHIVE_BY_ID.formatted(completedTable, table, completedTable);
		this.sqlStatementCopyToArchiveByEventAndListenerId = SQL_STATEMENT_COPY_TO_ARCHIVE_BY_EVENT_AND_LISTENER_ID.formatted(completedTable, table, completedTable);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#create(org.springframework.modulith.events.EventPublication)
	 */
	@Override
	@Transactional
	public TargetEventPublication create(TargetEventPublication publication) {

		var serializedEvent = serializeEvent(publication.getEvent());

		operations.update( //
				sqlStatementInsert, //
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

		var targetIdentifier = identifier.getValue();
		var serializedEvent = serializer.serialize(event);

		if (settings.isDeleteCompletion()) {

			operations.update(sqlStatementDeleteByEventAndListenerId, targetIdentifier, serializedEvent);

		} else if (settings.isArchiveCompletion()) {

			operations.update(sqlStatementCopyToArchiveByEventAndListenerId, //
					Timestamp.from(completionDate), //
					targetIdentifier, //
					serializedEvent);
			operations.update(sqlStatementDeleteByEventAndListenerId, targetIdentifier, serializedEvent);

		} else {

			operations.update(sqlStatementUpdateByEventAndListenerId, //
					Timestamp.from(completionDate), //
					targetIdentifier, //
					serializedEvent);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markCompleted(java.util.UUID, java.time.Instant)
	 */
	@Override
	@Transactional
	public void markCompleted(UUID identifier, Instant completionDate) {

		var databaseId = uuidToDatabase(identifier);
		var timestamp = Timestamp.from(completionDate);

		if (settings.isDeleteCompletion()) {
			operations.update(sqlStatementDeleteById, databaseId);

		} else if (settings.isArchiveCompletion()) {
			operations.update(sqlStatementCopyToArchive, timestamp, databaseId);
			operations.update(sqlStatementDeleteById, databaseId);

		} else {
			operations.update(sqlStatementUpdateById, timestamp, databaseId);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier( //
			Object event, PublicationTargetIdentifier targetIdentifier) {

		var result = operations.query(sqlStatementFindByEventAndListenerId, //
				this::resultSetToPublications, //
				serializeEvent(event), //
				targetIdentifier.getValue());

		return result == null ? Optional.empty() : result.stream().findFirst();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findCompletedPublications()
	 */
	@Override
	public List<TargetEventPublication> findCompletedPublications() {

		var result = operations.query(sqlStatementFindCompleted, this::resultSetToPublications);

		return result == null ? Collections.emptyList() : result;
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("null")
	public List<TargetEventPublication> findIncompletePublications() {
		return operations.query(sqlStatementFindUncompleted, this::resultSetToPublications);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsPublishedBefore(java.time.Instant)
	 */
	@Override
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {

		var result = operations.query(sqlStatementFindUncompletedBefore,
				this::resultSetToPublications, Timestamp.from(instant));

		return result == null ? Collections.emptyList() : result;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deletePublications(java.util.List)
	 */
	@Override
	public void deletePublications(List<UUID> identifiers) {

		var dbIdentifiers = identifiers.stream().map(this::uuidToDatabase).toList();

		batch(dbIdentifiers, DELETE_BATCH_SIZE)
				.forEach(it -> operations.update(sqlStatementDelete.concat(toParameterPlaceholders(it.length)), it));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	public void deleteCompletedPublications() {
		operations.execute(sqlStatementDeleteCompleted);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	public void deleteCompletedPublicationsBefore(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		operations.update(sqlStatementDeleteCompletedBefore, Timestamp.from(instant));
	}

	private String serializeEvent(Object event) {
		return serializer.serialize(event).toString();
	}

	/**
	 * Effectively a {@link org.springframework.jdbc.core.ResultSetExtractor} to drop {@link TargetEventPublication}s that cannot be deserialized.
	 *
	 * @param resultSet must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @throws SQLException
	 */
	private List<TargetEventPublication> resultSetToPublications(ResultSet resultSet) throws SQLException {

		List<TargetEventPublication> result = new ArrayList<>();

		while (resultSet.next()) {

			var publication = resultSetToPublication(resultSet);

			if (publication != null) {
				result.add(publication);
			}
		}

		return result;
	}

	/**
	 * Effectively a {@link RowMapper} to turn a single row into an {@link TargetEventPublication}.
	 *
	 * @param rs must not be {@literal null}.
	 * @return can be {@literal null}.
	 * @throws SQLException
	 */
	@Nullable
	private TargetEventPublication resultSetToPublication(ResultSet rs) throws SQLException {

		var id = getUuidFromResultSet(rs);
		var eventClass = loadClass(id, rs.getString("EVENT_TYPE"));

		if (eventClass == null) {
			return null;
		}

		var completionDate = rs.getTimestamp("COMPLETION_DATE");
		var publicationDate = rs.getTimestamp("PUBLICATION_DATE").toInstant();
		var listenerId = rs.getString("LISTENER_ID");
		var serializedEvent = rs.getString("SERIALIZED_EVENT");

		return new JdbcEventPublication(id, publicationDate, listenerId,
				() -> serializer.deserialize(serializedEvent, eventClass),
				completionDate == null ? null : completionDate.toInstant());
	}

	private Object uuidToDatabase(UUID id) {
		return settings.getDatabaseType().uuidToDatabase(id);
	}

	private UUID getUuidFromResultSet(ResultSet rs) throws SQLException {
		return settings.getDatabaseType().databaseToUUID(rs.getObject("ID"));
	}

	@Nullable
	private Class<?> loadClass(UUID id, String className) {

		try {
			return ClassUtils.forName(className, classLoader);
		} catch (ClassNotFoundException e) {
			LOGGER.warn("Event '{}' of unknown type '{}' found", id, className);
			return null;
		}
	}

	private static List<Object[]> batch(List<?> input, int batchSize) {

		var inputSize = input.size();

		return IntStream.range(0, (inputSize + batchSize - 1) / batchSize)
				.mapToObj(i -> input.subList(i * batchSize, Math.min((i + 1) * batchSize, inputSize)))
				.map(List::toArray)
				.toList();
	}

	private static String toParameterPlaceholders(int length) {

		return IntStream.range(0, length)
				.mapToObj(__ -> "?")
				.collect(Collectors.joining(", ", "(", ")"));
	}

	private static class JdbcEventPublication implements TargetEventPublication {

		private final UUID id;
		private final Instant publicationDate;
		private final String listenerId;
		private final Supplier<Object> eventSupplier;

		private @Nullable Instant completionDate;
		private @Nullable Object event;

		/**
		 * @param id must not be {@literal null}.
		 * @param publicationDate must not be {@literal null}.
		 * @param listenerId must not be {@literal null} or empty.
		 * @param event must not be {@literal null}..
		 * @param completionDate can be {@literal null}.
		 */
		public JdbcEventPublication(UUID id, Instant publicationDate, String listenerId, Supplier<Object> event,
				@Nullable Instant completionDate) {

			Assert.notNull(id, "Id must not be null!");
			Assert.notNull(publicationDate, "Publication date must not be null!");
			Assert.hasText(listenerId, "Listener id must not be null or empty!");
			Assert.notNull(event, "Event must not be null!");

			this.id = id;
			this.publicationDate = publicationDate;
			this.listenerId = listenerId;
			this.eventSupplier = event;
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
		@SuppressWarnings("null")
		public Object getEvent() {

			if (event == null) {
				this.event = eventSupplier.get();
			}

			return event;
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
					&& Objects.equals(id, that.id) //
					&& Objects.equals(listenerId, that.listenerId) //
					&& Objects.equals(publicationDate, that.publicationDate) //
					&& Objects.equals(getEvent(), that.getEvent());
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(completionDate, id, listenerId, publicationDate, getEvent());
		}
	}
}
