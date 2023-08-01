/*
 * Copyright 2023 the original author or authors.
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
package com.acme.myproject.moduleA;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for {@link ApplicationModuleTest}.
 *
 * @author Oliver Drotbohm
 */
@ApplicationModuleTest(verifyAutomatically = false)
class ApplicationModuleTestIntegrationTests {

	@Test // GH-173
	void bootstrapsFirstLevelTestMethod() {}

	@Nested
	class SomeNestedClass {

		@Test // GH-173
		void bootstrapsSecondLevelMestMethod() {}
	}

	// GH-253
	@Nested
	@ApplicationModuleTest(verifyAutomatically = false)
	@ContextConfiguration
	@AutoConfigureWebTestClient
	class SampleTest {

		@Autowired ApplicationContext context;

		@Test
		void registersTestWebClient() {
			assertThat(context.getBean(WebTestClient.class)).isNotNull();
		}
	}
}
