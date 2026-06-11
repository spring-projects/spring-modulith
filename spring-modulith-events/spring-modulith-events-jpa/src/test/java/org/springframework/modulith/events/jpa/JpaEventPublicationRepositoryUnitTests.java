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
package org.springframework.modulith.events.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.support.CompletionMode;

/**
 * Unit tests for {@link JpaEventPublicationRepository}.
 *
 * @author Oliver Drotbohm
 */
class JpaEventPublicationRepositoryUnitTests {

	@Test // GH-1701
	void deletesPublicationsByIdentifierInBatches() {

		var entityManager = mock(EntityManager.class);
		var query = mock(Query.class);
		var parameters = new ArrayList<List<UUID>>();

		when(entityManager.createQuery(anyString())).thenReturn(query);
		when(query.setParameter(eq(1), any())).thenAnswer(invocation -> {

			parameters.add(invocation.getArgument(1));
			return query;
		});

		var repository = new JpaEventPublicationRepository(entityManager, mock(EventSerializer.class),
				CompletionMode.UPDATE);
		var identifiers = new ArrayList<UUID>();

		IntStream.range(0, 101)
				.mapToObj(__ -> UUID.randomUUID())
				.forEach(identifiers::add);

		repository.deletePublications(identifiers);

		verify(query, times(2)).executeUpdate();

		assertThat(parameters).extracting(List::size).containsExactly(100, 1);
	}
}
