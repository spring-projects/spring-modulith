/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.events.couchbase;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.modulith.events.EventPublication.*;
import static org.springframework.modulith.events.couchbase.CouchbaseEventPublicationRepository.*;

/**
 * A CouchBase Document to represent event publications.
 *
 * @author Alexandre Vigneron
 */
@Document
@Collection(value = BASE_COLLECTION)
class CouchbaseEventPublication {

    @Id
    final UUID id;
    @Field
	final Instant publicationDate;
    @Field
	final String listenerId;
    @Field
	final Object event;
    @Field
    @Nullable
	final Instant lastResubmissionDate;
    @Field
	final int completionAttempts;

    @Field
	@Nullable
    Instant completionDate;
    @Field
	Status status;

    /**
     * Creates a new {@link CouchbaseEventPublication} for the given id, publication date, listener id, event and completion
     * date.
     *
     * @param id must not be {@literal null}.
     * @param publicationDate must not be {@literal null}.
     * @param listenerId must not be {@literal null} or empty.
     * @param event must not be {@literal null}.
     * @param completionDate can be {@literal null}.
     * @param status can be {@literal null}.
     * @param lastResubmissionDate can be {@literal null}.
     */
    @PersistenceCreator
    public CouchbaseEventPublication(UUID id, Instant publicationDate, String listenerId, Object event,
                                     @Nullable Instant completionDate, @Nullable Status status,
                                     @Nullable Instant lastResubmissionDate, int completionAttempts) {
        Assert.notNull(id, "Id must not be null!");
		Assert.notNull(publicationDate, "Publication date must not be null!");
		Assert.notNull(listenerId, "Listener id must not be null!");
		Assert.notNull(event, "Event must not be null!");

		this.id = id;
		this.publicationDate = publicationDate;
		this.listenerId = listenerId;
		this.event = event;
		this.completionDate = completionDate;
		this.status = status != null ? status : completionDate != null ? Status.COMPLETED : Status.PROCESSING;
		this.lastResubmissionDate = lastResubmissionDate;
		this.completionAttempts = completionAttempts;
    }

    /**
     * Marks the publication as completed at the given {@link Instant}.
     *
     * @param instant must not be {@literal null}.
     * @return will never be {@literal null}.
     */
    CouchbaseEventPublication markCompleted(Instant instant) {

        Assert.notNull(instant, "Instant must not be null!");

        this.completionDate = instant;
        this.status = Status.COMPLETED;

        return this;
    }

	/**
	 * Marks the publication as failed.
	 *
	 * @return will never be {@literal null}.
	 */
	CouchbaseEventPublication markFailed() {
		this.status = Status.FAILED;

		return this;
	}

	/**
	 * Marks the publication as resubmitted at the given {@link Instant}.
	 *
	 * @param instant must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	CouchbaseEventPublication markResubmitted(Instant instant) {
		Assert.notNull(instant, "Instant must not be null!");

		return new CouchbaseEventPublication(
				id, publicationDate, listenerId, event, completionDate,
				Status.RESUBMITTED, instant, this.completionAttempts + 1
		);
	}
}
