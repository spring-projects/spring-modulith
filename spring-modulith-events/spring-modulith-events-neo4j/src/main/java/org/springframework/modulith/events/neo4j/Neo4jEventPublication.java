package org.springframework.modulith.events.neo4j;

import java.time.Instant;
import java.util.UUID;

/**
 *
 * The event publication entity definition.
 *
 * @author Gerrit Meier
 */
public class Neo4jEventPublication {

	public final UUID identifier;
	public final Instant publicationDate;
	public final String listenerId;
	public final Object event;
	public final String eventHash;

	public Instant completionDate;

	public Neo4jEventPublication(UUID identifier, Instant publicationDate, String listenerId, Object event, String eventHash) {
		this.identifier = identifier;
		this.publicationDate = publicationDate;
		this.listenerId = listenerId;
		this.event = event;
		this.eventHash = eventHash;
	}

}
