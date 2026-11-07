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
package org.springframework.modulith.events.jdbc;

import static org.assertj.core.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;

/**
 * Integration tests for the registration of {@link JdbcEventPublicationAutoConfiguration} for JDBC-based slice tests.
 *
 * @author Hyun Lee
 */
class JdbcEventPublicationSliceAutoConfigurationIntegrationTests {

	@TestFactory // GH-1763
	Stream<DynamicTest> registersEventPublicationRepositoryForSlices() {

		return DynamicTest.stream(Stream.of(JdbcSlicing.class, DataJdbcSlicing.class),
				it -> getTestAnnotationName(it) + " registers the event publication repository",
				it -> new ApplicationContextRunner()
						.withUserConfiguration(it)
						.run(context -> {
							assertThat(context).hasNotFailed();
							assertThat(context).hasSingleBean(EventPublicationRepository.class);
							assertThat(context).hasSingleBean(EventSerializer.class);
						}));
	}

	private static String getTestAnnotationName(Class<?> type) {

		return Stream.of(type.getDeclaredAnnotations())
				.map(Annotation::annotationType)
				.map(Class::getSimpleName)
				.filter(it -> it.endsWith("Test"))
				.findFirst()
				.orElseThrow();
	}

	@TestConfiguration
	@JdbcTest
	static class JdbcSlicing {}

	@TestConfiguration
	@DataJdbcTest
	@AutoConfigurationPackage
	static class DataJdbcSlicing {}
}
