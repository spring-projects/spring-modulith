/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moduliths.events.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Oliver Gierke
 */
@Data
@Entity
@NoArgsConstructor(force = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class JpaEventPublication {

	private final @Id @Column(length = 16) UUID id;
	private final Instant publicationDate;
	private final String listenerId;
	private final String serializedEvent;
	private final Class<?> eventType;

	private Instant completionDate;

	@Builder
	static JpaEventPublication of(Instant publicationDate, String listenerId, Object serializedEvent,
			Class<?> eventType) {
		return new JpaEventPublication(UUID.randomUUID(), publicationDate, listenerId, serializedEvent.toString(),
				eventType);
	}

	JpaEventPublication markCompleted() {

		this.completionDate = Instant.now();
		return this;
	}
}
