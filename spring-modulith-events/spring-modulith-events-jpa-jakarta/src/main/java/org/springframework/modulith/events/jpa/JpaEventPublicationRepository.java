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
package org.springframework.modulith.events.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

/**
 * Repository to store {@link JpaEventPublication}s.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
public class JpaEventPublicationRepository {

	private final EntityManager entityManager;

	@Transactional
	JpaEventPublication create(JpaEventPublication publication) {

		entityManager.persist(publication);
		return publication;
	}

	@Transactional
	JpaEventPublication update(JpaEventPublication publication) {

		entityManager.merge(publication);
		entityManager.flush();

		return publication;
	}

	/**
	 * Returns all {@link JpaEventPublication} that have not been completed yet.
	 */
	@Transactional(readOnly = true)
	List<JpaEventPublication> findByCompletionDateIsNull() {

		String query = "select p from JpaEventPublication p where p.completionDate is null";

		return entityManager.createQuery(query, JpaEventPublication.class).getResultList();
	}

	/**
	 * Return the {@link JpaEventPublication} for the given serialized event and listener identifier.
	 *
	 * @param event must not be {@literal null}.
	 * @param listenerId must not be {@literal null}.
	 * @return
	 */
	@Transactional(readOnly = true)
	Optional<JpaEventPublication> findBySerializedEventAndListenerId(Object event, String listenerId) {

		String query = "select p from JpaEventPublication p where p.serializedEvent = ?1 and p.listenerId = ?2";

		TypedQuery<JpaEventPublication> typedQuery = entityManager.createQuery(query, JpaEventPublication.class)
				.setParameter(1, event)
				.setParameter(2, listenerId);

		try {
			return Optional.of(typedQuery.getSingleResult());
		} catch (Exception o_O) {
			return Optional.empty();
		}
	}
}
