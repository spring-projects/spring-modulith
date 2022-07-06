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
package org.moduliths.events.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import lombok.RequiredArgsConstructor;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.moduliths.events.EventSerializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Oliver Drotbohm
 */
@ExtendWith(SpringExtension.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@RequiredArgsConstructor
public class JpaEventPublicationConfigurationIntegrationTests {

	private final ApplicationContext context;

	@Configuration
	@Import(JpaEventPublicationConfiguration.class)
	static class TestConfig {

		@Bean
		EventSerializer eventSerializer() {
			return mock(EventSerializer.class);
		}

		@Bean
		EntityManager entityManager() {

			EntityManager em = mock(EntityManager.class);

			// Mock API for query executed at bootstrap time
			TypedQuery<?> query = mock(TypedQuery.class);
			doReturn(query).when(em).createQuery(any(String.class), any());

			return em;
		}
	}

	@Test
	void bootstrapsApplicationComponents() {

		assertThat(context.getBean(JpaEventPublicationRegistry.class)).isNotNull();
		assertThat(context.getBean(JpaEventPublicationRepository.class)).isNotNull();
	}
}
