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
package org.springframework.modulith.events;

import java.util.List;
import java.util.Optional;

/**
 * Repository to store {@link EventPublication}s.
 *
 * @author Björn Kieling
 * @author Dmitry Belyaev
 * @author Oliver Drotbohm
 */
public interface EventPublicationRepository {

	/**
	 * Persists the given {@link EventPublication}.
	 *
	 * @param publication must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	EventPublication create(EventPublication publication);

	/**
	 * Update the data store to mark the backing log entry as completed.
	 *
	 * @param publication must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	EventPublication update(CompletableEventPublication publication);

	/**
	 * Returns all {@link EventPublication} that have not been completed yet.
	 *
	 * @return will never be {@literal null}.
	 */
	List<EventPublication> findIncompletePublications();

	/**
	 * Return the {@link EventPublication} for the given serialized event and listener identifier.
	 *
	 * @param event must not be {@literal null}.
	 * @param targetIdentifier must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Optional<EventPublication> findByEventAndTargetIdentifier(Object event, PublicationTargetIdentifier targetIdentifier);
}
