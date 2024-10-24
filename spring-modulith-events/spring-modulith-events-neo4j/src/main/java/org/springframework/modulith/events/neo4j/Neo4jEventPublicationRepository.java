/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.modulith.events.neo4j;

import static org.neo4j.cypherdsl.core.Cypher.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.ResultStatement;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.events.support.CompletionMode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

/**
 * A {@link Neo4jClient} based implementation of {@link EventPublicationRepository}.
 *
 * @author Gerrit Meier
 * @author Oliver Drotbohm
 * @author Cora Iberkleid
 * @since 1.1
 */
@Transactional
class Neo4jEventPublicationRepository implements EventPublicationRepository {

	private static final String ID = "identifier";
	private static final String EVENT_SERIALIZED = "eventSerialized";
	private static final String EVENT_HASH = "eventHash";
	private static final String EVENT_TYPE = "eventType";
	private static final String LISTENER_ID = "listenerId";
	private static final String PUBLICATION_DATE = "publicationDate";
	private static final String COMPLETION_DATE = "completionDate";

	private static final Node EVENT_PUBLICATION_NODE = node("Neo4jEventPublication")
			.named("neo4jEventPublication");

	private static final Node EVENT_PUBLICATION_COMPLETED_NODE = node("Neo4jEventPublicationCompleted")
			.named("neo4jEventPublicationCompleted");

	private static final Statement INCOMPLETE_BY_EVENT_AND_TARGET_IDENTIFIER_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(EVENT_HASH).eq(parameter(EVENT_HASH)))
			.and(EVENT_PUBLICATION_NODE.property(LISTENER_ID).eq(parameter(LISTENER_ID)))
			.and(EVENT_PUBLICATION_NODE.property(COMPLETION_DATE).isNull())
			.returning(EVENT_PUBLICATION_NODE)
			.build();

	private static final Statement DELETE_BY_EVENT_AND_LISTENER_ID = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(EVENT_HASH).eq(parameter(EVENT_HASH)))
			.and(EVENT_PUBLICATION_NODE.property(LISTENER_ID).eq(parameter(LISTENER_ID)))
			.delete(EVENT_PUBLICATION_NODE)
			.build();

	private static final Statement DELETE_BY_ID_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(ID).in(parameter(ID)))
			.delete(EVENT_PUBLICATION_NODE)
			.build();

	private static final Function<Node, Statement> DELETE_COMPLETED_STATEMENT = node -> match(node)
			.where(node.property(COMPLETION_DATE).isNotNull())
			.delete(node)
			.build();

	private static final Function<Node, Statement> DELETE_COMPLETED_BEFORE_STATEMENT = node -> match(node)
			.where(node.property(PUBLICATION_DATE).lt(parameter(PUBLICATION_DATE)))
			.and(node.property(COMPLETION_DATE).isNotNull())
			.delete(node)
			.build();

	private static final Statement INCOMPLETE_PUBLISHED_BEFORE_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(PUBLICATION_DATE).lt(parameter(PUBLICATION_DATE)))
			.and(EVENT_PUBLICATION_NODE.property(COMPLETION_DATE).isNull())
			.returning(EVENT_PUBLICATION_NODE)
			.orderBy(EVENT_PUBLICATION_NODE.property(PUBLICATION_DATE))
			.build();

	private static final Statement CREATE_STATEMENT = Cypher.create(EVENT_PUBLICATION_NODE)
			.set(EVENT_PUBLICATION_NODE.property(ID).to(parameter(ID)))
			.set(EVENT_PUBLICATION_NODE.property(EVENT_SERIALIZED).to(parameter(EVENT_SERIALIZED)))
			.set(EVENT_PUBLICATION_NODE.property(EVENT_HASH).to(parameter(EVENT_HASH)))
			.set(EVENT_PUBLICATION_NODE.property(EVENT_TYPE).to(parameter(EVENT_TYPE)))
			.set(EVENT_PUBLICATION_NODE.property(LISTENER_ID).to(parameter(LISTENER_ID)))
			.set(EVENT_PUBLICATION_NODE.property(PUBLICATION_DATE).to(parameter(PUBLICATION_DATE)))
			.build();

	private static final Statement COMPLETE_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(EVENT_HASH).eq(parameter(EVENT_HASH)))
			.and(EVENT_PUBLICATION_NODE.property(LISTENER_ID).eq(parameter(LISTENER_ID)))
			.and(EVENT_PUBLICATION_NODE.property(COMPLETION_DATE).isNull())
			.set(EVENT_PUBLICATION_NODE.property(COMPLETION_DATE).to(parameter(COMPLETION_DATE)))
			.build();

	private static final Statement COMPLETE_IN_ARCHIVE_BY_ID_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(ID).eq(parameter(ID)))
			.and(not(exists(match(EVENT_PUBLICATION_COMPLETED_NODE)
					.where(EVENT_PUBLICATION_COMPLETED_NODE.property(ID).eq(parameter(ID)))
					.returning(literalTrue()).build())))
			.with(EVENT_PUBLICATION_NODE)
			.create(EVENT_PUBLICATION_COMPLETED_NODE)
			.set(EVENT_PUBLICATION_COMPLETED_NODE.property(ID).to(EVENT_PUBLICATION_NODE.property(ID)))
			.set(EVENT_PUBLICATION_COMPLETED_NODE.property(COMPLETION_DATE).to(parameter(COMPLETION_DATE)))
			.build();

	private static final Statement COMPLETE_IN_ARCHIVE_BY_EVENT_AND_LISTENER_ID_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(EVENT_HASH).eq(parameter(EVENT_HASH)))
			.and(EVENT_PUBLICATION_NODE.property(LISTENER_ID).eq(parameter(LISTENER_ID)))
			.and(not(exists(match(EVENT_PUBLICATION_COMPLETED_NODE)
					.where(EVENT_PUBLICATION_COMPLETED_NODE.property(EVENT_HASH).eq(parameter(EVENT_HASH)))
					.and(EVENT_PUBLICATION_COMPLETED_NODE.property(LISTENER_ID).eq(parameter(LISTENER_ID)))
					.returning(literalTrue()).build())))
			.with(EVENT_PUBLICATION_NODE)
			.create(EVENT_PUBLICATION_COMPLETED_NODE)
			.set(EVENT_PUBLICATION_COMPLETED_NODE.property(ID).to(EVENT_PUBLICATION_NODE.property(ID)))
			.set(EVENT_PUBLICATION_COMPLETED_NODE.property(COMPLETION_DATE).to(parameter(COMPLETION_DATE)))
			.build();

	private static final Function<Node, Statement> COMPLETE_BY_ID_STATEMENT = node -> match(node)
			.where(node.property(ID).eq(parameter(ID)))
			.set(node.property(COMPLETION_DATE).to(parameter(COMPLETION_DATE)))
			.build();

	private static final ResultStatement INCOMPLETE_STATEMENT = match(EVENT_PUBLICATION_NODE)
			.where(EVENT_PUBLICATION_NODE.property(COMPLETION_DATE).isNull())
			.returning(EVENT_PUBLICATION_NODE)
			.orderBy(EVENT_PUBLICATION_NODE.property(PUBLICATION_DATE))
			.build();

	private static final Function<Node, ResultStatement> ALL_COMPLETED_STATEMENT = node -> match(node)
			.where(node.property(COMPLETION_DATE).isNotNull())
			.returning(node)
			.orderBy(node.property(PUBLICATION_DATE))
			.build();

	private final Neo4jClient neo4jClient;
	private final Renderer renderer;
	private final EventSerializer eventSerializer;
	private final CompletionMode completionMode;

	private final Statement deleteCompletedStatement;
	private final Statement deleteCompletedBeforeStatement;
	private final Statement completedByIdStatement;
	private final ResultStatement allCompletedStatement;

	Neo4jEventPublicationRepository(Neo4jClient neo4jClient, Configuration cypherDslConfiguration,
			EventSerializer eventSerializer, CompletionMode completionMode) {

		Assert.notNull(neo4jClient, "Neo4jClient must not be null!");
		Assert.notNull(cypherDslConfiguration, "CypherDSL configuration must not be null!");
		Assert.notNull(eventSerializer, "EventSerializer must not be null!");
		Assert.notNull(completionMode, "Completion mode must not be null!");

		this.neo4jClient = neo4jClient;
		this.renderer = Renderer.getRenderer(cypherDslConfiguration);
		this.eventSerializer = eventSerializer;
		this.completionMode = completionMode;

		var archiveNode = completionMode == CompletionMode.ARCHIVE ? EVENT_PUBLICATION_COMPLETED_NODE : EVENT_PUBLICATION_NODE;

		this.deleteCompletedStatement = DELETE_COMPLETED_STATEMENT.apply(archiveNode);
		this.deleteCompletedBeforeStatement = DELETE_COMPLETED_BEFORE_STATEMENT.apply(archiveNode);
		this.completedByIdStatement = COMPLETE_BY_ID_STATEMENT.apply(archiveNode);
		this.allCompletedStatement = ALL_COMPLETED_STATEMENT.apply(archiveNode);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#create(org.springframework.modulith.events.EventPublication)
	 */
	@Override
	@Transactional
	public TargetEventPublication create(TargetEventPublication publication) {

		var identifier = publication.getIdentifier();
		var publicationDate = publication.getPublicationDate();
		var listenerId = publication.getTargetIdentifier().getValue();
		var event = publication.getEvent();
		var eventType = event.getClass().getName();

		var eventSerialized = eventSerializer.serialize(event).toString();
		var eventHash = DigestUtils.md5DigestAsHex(eventSerialized.getBytes());

		neo4jClient.query(renderer.render(CREATE_STATEMENT))
				.bindAll(Map.of(
						ID, Values.value(identifier.toString()),
						EVENT_SERIALIZED, eventSerialized,
						EVENT_HASH, eventHash,
						EVENT_TYPE, eventType,
						LISTENER_ID, listenerId,
						PUBLICATION_DATE, Values.value(publicationDate.atOffset(ZoneOffset.UTC))))
				.run();

		return publication;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.EventPublicationRepository#markCompleted(java.lang.Object, org.springframework.modulith.events.PublicationTargetIdentifier, java.time.Instant)
	 */
	@Override
	@Transactional
	public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {

		var eventHash = DigestUtils.md5DigestAsHex(eventSerializer.serialize(event).toString().getBytes());

		if (completionMode == CompletionMode.DELETE) {

			neo4jClient.query(renderer.render(DELETE_BY_EVENT_AND_LISTENER_ID))
					.bind(eventHash).to(EVENT_HASH)
					.bind(identifier.getValue()).to(LISTENER_ID)
					.run();

		} else if (completionMode == CompletionMode.ARCHIVE) {

			neo4jClient.query(renderer.render(COMPLETE_IN_ARCHIVE_BY_EVENT_AND_LISTENER_ID_STATEMENT))
					.bind(eventHash).to(EVENT_HASH)
					.bind(identifier.getValue()).to(LISTENER_ID)
					.bind(Values.value(completionDate.atOffset(ZoneOffset.UTC))).to(COMPLETION_DATE)
					.run();
			neo4jClient.query(renderer.render(DELETE_BY_EVENT_AND_LISTENER_ID))
					.bind(eventHash).to(EVENT_HASH)
					.bind(identifier.getValue()).to(LISTENER_ID)
					.run();

		} else {

			neo4jClient.query(renderer.render(COMPLETE_STATEMENT))
					.bind(eventHash).to(EVENT_HASH)
					.bind(identifier.getValue()).to(LISTENER_ID)
					.bind(Values.value(completionDate.atOffset(ZoneOffset.UTC))).to(COMPLETION_DATE)
					.run();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#markCompleted(java.util.UUID, java.time.Instant)
	 */
	@Override
	@Transactional
	public void markCompleted(UUID identifier, Instant completionDate) {

		if (completionMode == CompletionMode.DELETE) {

			deletePublications(List.of(identifier));

		} else if (completionMode == CompletionMode.ARCHIVE) {

			neo4jClient.query(renderer.render(COMPLETE_IN_ARCHIVE_BY_ID_STATEMENT))
					.bind("").to(ID)
					.bind(Values.value(completionDate.atOffset(ZoneOffset.UTC))).to(COMPLETION_DATE)
					.run();
			deletePublications(List.of(identifier));

		} else {

			neo4jClient.query(renderer.render(completedByIdStatement))
					.bind(Values.value(identifier.toString())).to(ID)
					.bind(Values.value(completionDate.atOffset(ZoneOffset.UTC))).to(COMPLETION_DATE)
					.run();
		}

	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublications() {

		return List.copyOf(neo4jClient.query(renderer.render(INCOMPLETE_STATEMENT))
				.fetchAs(TargetEventPublication.class)
				.mappedBy(this::mapRecordToPublication)
				.all());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsPublishedBefore(java.time.Instant)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {

		return List.copyOf(neo4jClient.query(renderer.render(INCOMPLETE_PUBLISHED_BEFORE_STATEMENT))
				.bind(Values.value(instant.atOffset(ZoneOffset.UTC))).to(PUBLICATION_DATE)
				.fetchAs(TargetEventPublication.class)
				.mappedBy(this::mapRecordToPublication)
				.all());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(Object event,
			PublicationTargetIdentifier targetIdentifier) {

		var eventHash = DigestUtils.md5DigestAsHex(((String) eventSerializer.serialize(event)).getBytes());
		var listenerId = targetIdentifier.getValue();

		return neo4jClient.query(renderer.render(INCOMPLETE_BY_EVENT_AND_TARGET_IDENTIFIER_STATEMENT))
				.bindAll(Map.of(EVENT_HASH, eventHash, LISTENER_ID, listenerId))
				.fetchAs(TargetEventPublication.class)
				.mappedBy(this::mapRecordToPublication)
				.one();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findCompletedPublications()
	 */
	@Override
	public List<TargetEventPublication> findCompletedPublications() {

		return new ArrayList<>(neo4jClient.query(renderer.render(allCompletedStatement))
				.fetchAs(TargetEventPublication.class)
				.mappedBy(this::mapRecordToPublication)
				.all());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deletePublications(java.util.List)
	 */
	@Override
	@Transactional
	public void deletePublications(List<UUID> identifiers) {

		neo4jClient.query(renderer.render(DELETE_BY_ID_STATEMENT))
				.bind(identifiers.stream().map(UUID::toString).toList()).to(ID)
				.run();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deleteCompletedPublications()
	 */
	@Override
	@Transactional
	public void deleteCompletedPublications() {
		neo4jClient.query(renderer.render(deleteCompletedStatement)).run();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#deleteCompletedPublicationsBefore(java.time.Instant)
	 */
	@Override
	@Transactional
	public void deleteCompletedPublicationsBefore(Instant instant) {

		neo4jClient.query(renderer.render(deleteCompletedBeforeStatement))
				.bind(Values.value(instant.atOffset(ZoneOffset.UTC))).to(PUBLICATION_DATE)
				.run();
	}

	private Neo4jEventPublicationAdapter mapRecordToPublication(TypeSystem typeSystem, org.neo4j.driver.Record record) {

		var publicationNode = record.get(EVENT_PUBLICATION_NODE.getRequiredSymbolicName().getValue()).asNode();
		var identifier = UUID.fromString(publicationNode.get(ID).asString());
		var publicationDate = publicationNode.get(PUBLICATION_DATE).asZonedDateTime().toInstant();
		var listenerId = publicationNode.get(LISTENER_ID).asString();
		var eventSerialized = publicationNode.get(EVENT_SERIALIZED).asString();
		var eventHash = publicationNode.get(EVENT_HASH).asString();
		var eventType = publicationNode.get(EVENT_TYPE).asString();

		try {

			var event = eventSerializer.deserialize(eventSerialized, Class.forName(eventType));
			var publication = new Neo4jEventPublication(identifier, publicationDate, listenerId, event,
					eventHash);

			return new Neo4jEventPublicationAdapter(publication);

		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static class Neo4jEventPublicationAdapter implements TargetEventPublication {

		private final Neo4jEventPublication delegate;

		public Neo4jEventPublicationAdapter(Neo4jEventPublication delegate) {
			this.delegate = delegate;
		}

		@Override
		public UUID getIdentifier() {
			return delegate.identifier;
		}

		@Override
		public Object getEvent() {
			return delegate.event;
		}

		@Override
		public Instant getPublicationDate() {
			return delegate.publicationDate;
		}

		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.ofNullable(delegate.completionDate);
		}

		@Override
		public void markCompleted(Instant instant) {
			delegate.completionDate = instant;
		}

		@Override
		public PublicationTargetIdentifier getTargetIdentifier() {
			return PublicationTargetIdentifier.of(delegate.listenerId);
		}

		@Override
		public boolean isPublicationCompleted() {
			return delegate.completionDate != null;
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

			if (!(obj instanceof Neo4jEventPublicationAdapter that)) {
				return false;
			}

			return Objects.equals(delegate, that.delegate);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(delegate);
		}
	}
}
