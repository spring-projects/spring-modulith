/*
 * Copyright 2023-2025 the original author or authors.
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

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.EventPublication.Status;

/**
 * The event publication entity definition.
 *
 * @author Gerrit Meier
 * @since 1.1
 */
class Neo4jEventPublication {

	public final UUID identifier;
	public final Instant publicationDate;
	public final String listenerId;
	public final Object event;
	public final String eventHash;
	public final int completionAttempts;

	public @Nullable Instant completionDate;
	public @Nullable Instant lastResubmissionDate;
	public Status status;

	public Neo4jEventPublication(UUID identifier, Instant publicationDate, String listenerId, Object event,
			String eventHash, @Nullable Instant completionDate, Status status, int completionAttempts,
			@Nullable Instant lastResubmissionDate) {

		this.identifier = identifier;
		this.publicationDate = publicationDate;
		this.listenerId = listenerId;
		this.event = event;
		this.eventHash = eventHash;
		this.completionDate = completionDate;
		this.status = status;
		this.lastResubmissionDate = lastResubmissionDate;
		this.completionAttempts = completionAttempts;
	}
}
