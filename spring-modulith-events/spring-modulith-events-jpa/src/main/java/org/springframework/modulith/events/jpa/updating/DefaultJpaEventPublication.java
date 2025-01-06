/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.events.jpa.updating;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.jpa.JpaEventPublication;

@Entity(name = "DefaultJpaEventPublication")
@Table(name = "EVENT_PUBLICATION")
public class DefaultJpaEventPublication extends JpaEventPublication {

	public DefaultJpaEventPublication(UUID id, Instant publicationDate, String listenerId, String serializedEvent,
			Class<?> eventType) {
		super(id, publicationDate, listenerId, serializedEvent, eventType);
	}

	@SuppressWarnings("unused")
	DefaultJpaEventPublication() {}
}
