/*
 * Copyright 2022 the original author or authors.
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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A MongoDB Document to represent event publications.
 *
 * @author Dmitry Belyaev
 * @author Björn Kieling
 */
@Document(collection = "org_springframework_modulith_events")
@Getter
@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__(@PersistenceCreator))
class MongoDbEventPublication {

	@Id
	private String id;

	private final Instant publicationDate;
	private final String listenerId;
	private final Object event;

	@Setter(AccessLevel.PACKAGE)
	private Instant completionDate;
}
