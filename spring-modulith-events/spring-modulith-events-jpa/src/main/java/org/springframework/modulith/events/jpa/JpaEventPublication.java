/*
 * Copyright 2017-2023 the original author or authors.
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
package org.springframework.modulith.events.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.Assert;

/**
 * JPA entity to represent event publications.
 *
 * @author Oliver Drotbohm
 * @author Dmitry Belyaev
 * @author Björn Kieling
 */
@Entity
class JpaEventPublication {

	final @Id @Column(length = 16) UUID id;
	final Instant publicationDate;
	final String listenerId;
	final String serializedEvent;
	final Class<?> eventType;

	Instant completionDate;

	/**
	 * Creates a new {@link JpaEventPublication} for the given publication date, listener id, serialized event and event
	 * type.
	 *
	 * @param publicationDate must not be {@literal null}.
	 * @param listenerId must not be {@literal null} or empty.
	 * @param serializedEvent must not be {@literal null} or empty.
	 * @param eventType must not be {@literal null}.
	 */
	JpaEventPublication(Instant publicationDate, String listenerId, String serializedEvent, Class<?> eventType) {

		Assert.notNull(publicationDate, "Publication date must not be null!");
		Assert.notNull(listenerId, "Listener id must not be null or empty!");
		Assert.notNull(serializedEvent, "Serialized event must not be null or empty!");
		Assert.notNull(eventType, "Event type must not be null!");

		this.id = UUID.randomUUID();
		this.publicationDate = publicationDate;
		this.listenerId = listenerId;
		this.serializedEvent = serializedEvent;
		this.eventType = eventType;
	}

	JpaEventPublication() {

		this.id = null;
		this.publicationDate = null;
		this.listenerId = null;
		this.serializedEvent = null;
		this.eventType = null;
	}

	JpaEventPublication markCompleted() {

		this.completionDate = Instant.now();
		return this;
	}
}
