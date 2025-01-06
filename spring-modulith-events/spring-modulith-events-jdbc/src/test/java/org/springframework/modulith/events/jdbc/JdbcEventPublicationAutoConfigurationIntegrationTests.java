/*
 * Copyright 2022-2025 the original author or authors.
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
package org.springframework.modulith.events.jdbc;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.testapp.TestApplication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * @author Dmitry Belyaev
 * @author Björn Kieling
 * @author Oliver Drotbohm
 */
@SpringBootTest(
		classes = TestApplication.class,
		properties = "spring.modulith.events.jdbc.schema-initialization.enabled=true")
class JdbcEventPublicationAutoConfigurationIntegrationTests {

	@Autowired ApplicationContext context;

	@MockitoBean EventSerializer serializer;

	@Test // GH-3
	void bootstrapsApplicationComponents() {

		assertThat(context.getBean(EventPublicationRegistry.class)).isNotNull();
		assertThat(context.getBean(JdbcEventPublicationRepository.class)).isNotNull();
	}
}
