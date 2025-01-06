/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.modulith.test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import example.TestConfiguration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.test.EnableScenarioIntegrationTests.TransactionTemplateTestConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the usage of {@link EnableScenarios}.
 *
 * @author Oliver Drotbohm
 */
@Nested
@EnableScenarios
@SpringBootTest(classes = { TestConfiguration.class, TransactionTemplateTestConfiguration.class })
class EnableScenarioIntegrationTests {

	@Configuration
	static class TransactionTemplateTestConfiguration {

		@Bean
		TransactionTemplate transactionTemplate() {
			return new TransactionTemplate(mock(PlatformTransactionManager.class));
		}
	}

	@Test // GH-190
	void injectsScenario(Scenario scenario) {
		assertThat(scenario).isNotNull();
	}
}
