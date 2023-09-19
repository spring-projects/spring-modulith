package org.springframework.modulith.events.neo4j;

import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Transactional
class Neo4jEventPublicationRepository implements EventPublicationRepository {

	private static final String ID = "identifier";
	private static final String EVENT_SERIALIZED = "eventSerialized";
	private static final String EVENT_HASH = "eventHash";
	private static final String EVENT_TYPE = "eventType";
	private static final String LISTENER_ID = "listenerId";
	private static final String PUBLICATION_DATE = "publicationDate";
	private static final String COMPLETION_DATE = "completionDate";

	private static final Node eventPublicationNode = Cypher.node("Neo4jEventPublication").named("neo4jEventPublication");

	private final Neo4jClient neo4jClient;
	private final Configuration cypherDslConfiguration;
	private final EventSerializer eventSerializer;

	Neo4jEventPublicationRepository(Neo4jClient neo4jClient, Configuration cypherDslConfiguration, EventSerializer eventSerializer) {

		Assert.notNull(neo4jClient, "Neo4jClient must not be null!");
		Assert.notNull(cypherDslConfiguration, "CypherDSL configuration must not be null!");
		Assert.notNull(eventSerializer, "EventSerializer must not be null!");

		this.neo4jClient = neo4jClient;
		this.cypherDslConfiguration = cypherDslConfiguration;
		this.eventSerializer = eventSerializer;
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

		var eventSerialized = (String) eventSerializer.serialize(event);
		var eventHash = DigestUtils.md5DigestAsHex(eventSerialized.getBytes());

		var createStatement = Cypher.create(eventPublicationNode)
			.set(eventPublicationNode.property(ID).to(Cypher.parameter(ID)))
			.set(eventPublicationNode.property(EVENT_SERIALIZED).to(Cypher.parameter(EVENT_SERIALIZED)))
			.set(eventPublicationNode.property(EVENT_HASH).to(Cypher.parameter(EVENT_HASH)))
			.set(eventPublicationNode.property(EVENT_TYPE).to(Cypher.parameter(EVENT_TYPE)))
			.set(eventPublicationNode.property(LISTENER_ID).to(Cypher.parameter(LISTENER_ID)))
			.set(eventPublicationNode.property(PUBLICATION_DATE).to(Cypher.parameter(PUBLICATION_DATE)))
			.build();

		neo4jClient.query(renderStatement(createStatement))
			.bindAll(Map.of(
				ID, Values.value(identifier.toString()),
				EVENT_SERIALIZED, eventSerialized,
				EVENT_HASH, eventHash,
				EVENT_TYPE, eventType,
				LISTENER_ID, listenerId,
				PUBLICATION_DATE, Values.value(publicationDate.atOffset(ZoneOffset.UTC))
			))
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
		var eventHash = DigestUtils.md5DigestAsHex(((String) eventSerializer.serialize(event)).getBytes());

		var completeStatement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(EVENT_HASH).eq(Cypher.parameter(EVENT_HASH)))
			.and(eventPublicationNode.property(LISTENER_ID).eq(Cypher.parameter(LISTENER_ID)))
			.set(eventPublicationNode.property(COMPLETION_DATE).to(Cypher.parameter(COMPLETION_DATE)))
			.build();

		neo4jClient.query(renderStatement(completeStatement))
			.bind(eventHash).to(EVENT_HASH)
			.bind(identifier.getValue()).to(LISTENER_ID)
			.bind(Values.value(completionDate.atOffset(ZoneOffset.UTC))).to(COMPLETION_DATE)
			.run();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.events.core.EventPublicationRepository#findIncompletePublicationsByEventAndTargetIdentifier(java.lang.Object, org.springframework.modulith.events.core.PublicationTargetIdentifier)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublications() {

		var findIncompleteStatement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(COMPLETION_DATE).isNull())
			.returning(eventPublicationNode)
			.orderBy(eventPublicationNode.property(PUBLICATION_DATE))
			.build();

		return List.copyOf(neo4jClient.query(renderStatement(findIncompleteStatement))
			.fetchAs(TargetEventPublication.class)
			.mappedBy(this::mapRecordToPublication)
			.all());

	}

	@Override
	@Transactional(readOnly = true)
	public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {
		var findIncompleteStatement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(PUBLICATION_DATE).lt(Cypher.parameter(PUBLICATION_DATE)))
			.and(eventPublicationNode.property(COMPLETION_DATE).isNull())
			.returning(eventPublicationNode)
			.orderBy(eventPublicationNode.property(PUBLICATION_DATE))
			.build();

		return List.copyOf(neo4jClient.query(renderStatement(findIncompleteStatement))
			.bind(Values.value(instant.atOffset(ZoneOffset.UTC))).to(PUBLICATION_DATE)
			.fetchAs(TargetEventPublication.class)
			.mappedBy(this::mapRecordToPublication)
			.all());

	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(Object event, PublicationTargetIdentifier targetIdentifier) {

		var eventHash = DigestUtils.md5DigestAsHex(((String) eventSerializer.serialize(event)).getBytes());
		var listenerId = targetIdentifier.getValue();

		var statement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(EVENT_HASH).eq(Cypher.parameter(EVENT_HASH)))
			.and(eventPublicationNode.property(LISTENER_ID).eq(Cypher.parameter(LISTENER_ID)))
			.and(eventPublicationNode.property(COMPLETION_DATE).isNull())
			.returning(eventPublicationNode)
			.build();

		return neo4jClient.query(renderStatement(statement))
			.bindAll(Map.of(EVENT_HASH, eventHash, LISTENER_ID, listenerId))
			.fetchAs(TargetEventPublication.class)
			.mappedBy(this::mapRecordToPublication)
			.one();
	}

	@Override
	@Transactional
	public void deletePublications(List<UUID> identifiers) {
		var deleteStatement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(ID).in(Cypher.parameter(ID)))
			.delete(eventPublicationNode)
			.build();

		neo4jClient.query(renderStatement(deleteStatement))
			.bind(identifiers.stream().map(UUID::toString).toList()).to(ID)
			.run();
	}

	@Override
	@Transactional
	public void deleteCompletedPublications() {
		var deleteStatement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(COMPLETION_DATE).isNotNull())
			.delete(eventPublicationNode)
			.build();

		neo4jClient.query(renderStatement(deleteStatement))
			.run();
	}

	@Override
	@Transactional
	public void deleteCompletedPublicationsBefore(Instant instant) {
		var deleteStatement = Cypher.match(eventPublicationNode)
			.where(eventPublicationNode.property(PUBLICATION_DATE).lt(Cypher.parameter(PUBLICATION_DATE)))
			.and(eventPublicationNode.property(COMPLETION_DATE).isNotNull())
			.delete(eventPublicationNode)
			.build();

		neo4jClient.query(renderStatement(deleteStatement))
			.bind(Values.value(instant.atOffset(ZoneOffset.UTC))).to(PUBLICATION_DATE)
			.run();
	}

	private Neo4jEventPublicationAdapter mapRecordToPublication(TypeSystem typeSystem, org.neo4j.driver.Record record) {
		var publicationNode = record.get(eventPublicationNode.getRequiredSymbolicName().getValue()).asNode();
		var identifier = UUID.fromString(publicationNode.get(ID).asString());
		var publicationDate = publicationNode.get(PUBLICATION_DATE).asZonedDateTime().toInstant();
		var listenerId = publicationNode.get(LISTENER_ID).asString();
		var eventSerialized = publicationNode.get(EVENT_SERIALIZED).asString();
		var eventHash = publicationNode.get(EVENT_HASH).asString();
		var eventType = publicationNode.get(EVENT_TYPE).asString();

		try {
			Object event = eventSerializer.deserialize(eventSerialized, Class.forName(eventType));
			Neo4jEventPublication publication = new Neo4jEventPublication(identifier, publicationDate, listenerId, event, eventHash);

			return new Neo4jEventPublicationAdapter(publication);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private String renderStatement(Statement statement) {
		return Renderer.getRenderer(cypherDslConfiguration).render(statement);
	}


	public static class Neo4jEventPublicationAdapter implements TargetEventPublication {

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
		public boolean equals(Object obj) {

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