package org.springframework.modulith.events.neo4j;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.DigestUtils;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Gerrit Meier
 */
@SpringJUnitConfig(Neo4jEventPublicationRepositoryTest.Config.class)
@Testcontainers(disabledWithoutDocker = true)
class Neo4jEventPublicationRepositoryTest {

	@Container //
	static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5"))
			.withRandomPassword();

	static final PublicationTargetIdentifier TARGET_IDENTIFIER = PublicationTargetIdentifier.of("listener");

	@Autowired Neo4jEventPublicationRepository repository;
	@Autowired Driver driver;
	@MockBean EventSerializer eventSerializer;

	@BeforeEach
	void clearDb() {

		try (var session = driver.session()) {
			session.run("MATCH (n) detach delete n").consume();
		}
	}

	@Test
	void createEventPublication() {

		var testEvent = new TestEvent("id");
		var eventSerialized = "{\"eventId\":\"id\"}";
		var eventHash = DigestUtils.md5DigestAsHex(eventSerialized.getBytes());

		when(eventSerializer.serialize(testEvent)).thenReturn(eventSerialized);
		var publication = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

		try (var session = driver.session()) {

			var result = session.run("MATCH (p:Neo4jEventPublication) return p")
					.single();

			var neo4jEventPublicationNode = result.get("p").asNode();

			assertThat(UUID.fromString(neo4jEventPublicationNode.get("identifier").asString()))
					.isEqualTo(publication.getIdentifier());
			assertThat(neo4jEventPublicationNode.get("publicationDate").asZonedDateTime().toInstant())
					.isEqualTo(publication.getPublicationDate());
			assertThat(neo4jEventPublicationNode.get("listenerId").asString())
					.isEqualTo(publication.getTargetIdentifier().getValue());
			assertThat(neo4jEventPublicationNode.get("completionDate").isNull()).isTrue();
			assertThat(neo4jEventPublicationNode.get("eventSerialized").asString()).isEqualTo(eventSerialized);
			assertThat(neo4jEventPublicationNode.get("eventHash").asString()).isEqualTo(eventHash);
		}
	}

	@Test
	void updateEventPublication() {

		var testEvent1 = new TestEvent("id1");
		var event1Serialized = "{\"eventId\":\"id1\"}";
		var testEvent2 = new TestEvent("id2");
		var event2Serialized = "{\"eventId\":\"id2\"}";

		when(eventSerializer.serialize(testEvent1)).thenReturn(event1Serialized);
		when(eventSerializer.serialize(testEvent2)).thenReturn(event2Serialized);
		when(eventSerializer.deserialize(event2Serialized, TestEvent.class)).thenReturn(testEvent2);

		var event1 = repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));
		var event2 = repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));

		var now = Instant.now();
		repository.markCompleted(event1, now);

		assertThat(repository.findIncompletePublications()).hasSize(1)
				.element(0)
				.extracting(TargetEventPublication::getEvent).isEqualTo(event2.getEvent());
	}

	@Test
	void findInCompletePastPublications() {

		var testEvent = new TestEvent("id");
		var eventSerialized = "{\"eventId\":\"id\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(eventSerialized);
		when(eventSerializer.deserialize(eventSerialized, TestEvent.class)).thenReturn(testEvent);

		var event = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

		var newer = Instant.now().plus(1L, ChronoUnit.MINUTES);
		var older = Instant.now().minus(1L, ChronoUnit.MINUTES);

		assertThat(repository.findIncompletePublicationsPublishedBefore(newer)).hasSize(1)
				.element(0)
				.extracting(TargetEventPublication::getEvent).isEqualTo(event.getEvent());

		assertThat(repository.findIncompletePublicationsPublishedBefore(older)).hasSize(0);
	}

	@Test
	void findIncompleteByEventAndTargetIdentifier() {

		var testEvent = new TestEvent("id");
		var eventSerialized = "{\"eventId\":\"id\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(eventSerialized);
		when(eventSerializer.deserialize(eventSerialized, TestEvent.class)).thenReturn(testEvent);

		var event = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));

		assertThat(repository.findIncompletePublicationsByEventAndTargetIdentifier(testEvent, event.getTargetIdentifier()))
				.isPresent();
	}

	@Test
	void deletePublicationById() {

		var testEvent = new TestEvent("id");
		var eventSerialized = "{\"eventId\":\"id\"}";

		when(eventSerializer.serialize(testEvent)).thenReturn(eventSerialized);

		var event = repository.create(TargetEventPublication.of(testEvent, TARGET_IDENTIFIER));
		assertThat(repository.findIncompletePublications()).hasSize(1);

		repository.deletePublications(List.of(event.getIdentifier()));
		assertThat(repository.findIncompletePublications()).hasSize(0);
	}

	@Test
	void deleteCompletedPublications() {

		var testEvent1 = new TestEvent("id1");
		var event1Serialized = "{\"eventId\":\"id1\"}";
		var testEvent2 = new TestEvent("id2");
		var event2Serialized = "{\"eventId\":\"id2\"}";

		when(eventSerializer.serialize(testEvent1)).thenReturn(event1Serialized);
		when(eventSerializer.serialize(testEvent2)).thenReturn(event2Serialized);
		when(eventSerializer.deserialize(event1Serialized, TestEvent.class)).thenReturn(testEvent1);
		when(eventSerializer.deserialize(event2Serialized, TestEvent.class)).thenReturn(testEvent2);

		var event1 = repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));

		repository.markCompleted(event1, Instant.now());

		repository.deleteCompletedPublications();

		try (var session = driver.session()) {
			var count = session.run("MATCH (n) WHERE n.completionDate is not null return count(n)").single().get("count(n)")
					.asLong();
			assertThat(count).isEqualTo(0);
		}
	}

	@Test
	void deleteCompletedPublicationsBefore() throws Exception {

		var testEvent1 = new TestEvent("id1");
		var event1Serialized = "{\"eventId\":\"id1\"}";
		var testEvent2 = new TestEvent("id2");
		var event2Serialized = "{\"eventId\":\"id2\"}";

		when(eventSerializer.serialize(testEvent1)).thenReturn(event1Serialized);
		when(eventSerializer.serialize(testEvent2)).thenReturn(event2Serialized);
		when(eventSerializer.deserialize(event1Serialized, TestEvent.class)).thenReturn(testEvent1);
		when(eventSerializer.deserialize(event2Serialized, TestEvent.class)).thenReturn(testEvent2);

		var event1 = repository.create(TargetEventPublication.of(testEvent1, TARGET_IDENTIFIER));

		Instant old = Instant.now();
		repository.markCompleted(event1, old);
		Thread.sleep(100);
		var event2 = repository.create(TargetEventPublication.of(testEvent2, TARGET_IDENTIFIER));
		repository.markCompleted(event2, Instant.now());

		repository.deleteCompletedPublicationsBefore(old.plus(10, ChronoUnit.MILLIS));
		// defensive check just to be sure
		assertThat(repository.findIncompletePublications()).hasSize(0);

		try (var session = driver.session()) {

			var records = session.run("MATCH (n) WHERE n.completionDate is not null return n").list();

			assertThat(records.size()).isEqualTo(1);
			assertThat(records.get(0).get("n").asNode().get("eventSerialized").asString()).contains("id2");
		}
	}

	@Test // GH-452
	void findsCompletedPublications() {

		var event = new TestEvent("first");
		var publication = createPublication(event);

		repository.markCompleted(publication, Instant.now());

		assertThat(repository.findCompletedPublications())
				.hasSize(1)
				.element(0)
				.extracting(TargetEventPublication::getEvent)
				.isEqualTo(event);
	}

	private TargetEventPublication createPublication(Object event) {

		var token = event.toString();

		doReturn(token).when(eventSerializer).serialize(event);
		doReturn(event).when(eventSerializer).deserialize(token, event.getClass());

		return repository.create(TargetEventPublication.of(event, TARGET_IDENTIFIER));
	}

	@Value
	static class TestEvent {
		String eventId;
	}

	@Import({ TestApplication.class })
	@Configuration
	static class Config {

		@Bean
		Driver driver() {
			return GraphDatabase.driver(neo4jContainer.getBoltUrl(),
					AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
		}

		@Bean
		org.neo4j.cypherdsl.core.renderer.Configuration cypherDslConfiguration() {
			return org.neo4j.cypherdsl.core.renderer.Configuration.newConfig().withDialect(Dialect.NEO4J_5).build();
		}
	}
}
