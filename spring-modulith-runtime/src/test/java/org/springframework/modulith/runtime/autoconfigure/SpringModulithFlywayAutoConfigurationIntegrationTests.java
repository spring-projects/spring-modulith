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
package org.springframework.modulith.runtime.autoconfigure;

import static org.assertj.core.api.Assertions.*;

import example.SampleApplication;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration.SpringModulithFlywayAutoConfiguration;
import org.springframework.modulith.runtime.test.DataJdbcSlicing;
import org.springframework.modulith.runtime.test.DataJpaSlicing;
import org.springframework.modulith.runtime.test.DataR2dbcSlicing;
import org.springframework.modulith.runtime.test.JdbcSlicing;
import org.springframework.modulith.runtime.test.JooqSlicing;

/**
 * Integration tests for
 * {@link org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration.SpringModulithFlywayAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class SpringModulithFlywayAutoConfigurationIntegrationTests {

	@TestFactory // GH-1706
	Stream<DynamicTest> activatesFlywayAutoConfigurationForSlices() {

		var testTypes = Stream.of(DataJdbcSlicing.class,
				DataJpaSlicing.class,
				DataR2dbcSlicing.class,
				JdbcSlicing.class,
				JooqSlicing.class);

		return DynamicTest.stream(testTypes,
				it -> getTestAnnotationName(it) + " enables runtime auto-configuration",
				it -> {

					new ApplicationContextRunner()
							.withUserConfiguration(SampleApplication.class)
							.withUserConfiguration(it)
							.run(ctxt -> {
								assertThat(ctxt).hasSingleBean(SpringModulithFlywayAutoConfiguration.class);
							});
				});
	}

	private String getTestAnnotationName(Class<?> type) {

		return Stream.of(type.getDeclaredAnnotations())
				.map(Annotation::annotationType)
				.map(Class::getSimpleName)
				.filter(it -> it.endsWith("Test"))
				.findFirst()
				.orElseThrow(
						() -> new IllegalArgumentException("No annotation ending in …Test found on %s!".formatted(type.getName())));
	}
}
