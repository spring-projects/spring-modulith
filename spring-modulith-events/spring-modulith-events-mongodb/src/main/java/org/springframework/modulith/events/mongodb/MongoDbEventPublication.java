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
package org.springframework.modulith.events.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A MongoDB Document to represent event publications.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 */
@Document(collection = "org_springframework_modulith_events")
class MongoDbEventPublication {

	final UUID id;
	final Instant publicationDate;
	final String listenerId;
	final Object event;

	@Nullable Instant completionDate;

	/**
	 * Creates a new {@link MongoDbEventPublication} for the given id, publication date, listener id, event and completion
	 * date.
	 *
	 * @param id must not be {@literal null}.
	 * @param publicationDate must not be {@literal null}.
	 * @param listenerId must not be {@literal null} or empty.
	 * @param event must not be {@literal null}.
	 * @param completionDate can be {@literal null}.
	 */
	@PersistenceCreator
	MongoDbEventPublication(UUID id, Instant publicationDate, String listenerId, Object event,
			@Nullable Instant completionDate) {

		Assert.notNull(id, "Id must not be null!");
		Assert.notNull(publicationDate, "Publication date must not be null!");
		Assert.notNull(listenerId, "Listener id must not be null!");
		Assert.notNull(event, "Event must not be null!");

		this.id = id;
		this.publicationDate = publicationDate;
		this.listenerId = listenerId;
		this.event = event;
		this.completionDate = completionDate;
	}

	/**
	 * Creates a new {@link MongoDbEventPublication} for the given publication date, listener id and event.
	 *
	 * @param publicationDate must not be {@literal null}.
	 * @param listenerId must not be {@literal null}.
	 * @param event must not be {@literal null}.
	 */
	MongoDbEventPublication(UUID id, Instant publicationDate, String listenerId, Object event) {
		this(id, publicationDate, listenerId, event, null);
	}

	/**
	 * Marks the publication as completed at the given {@link Instant}.
	 *
	 * @param instant must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	MongoDbEventPublication markCompleted(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		this.completionDate = instant;
		return this;
	}
}
