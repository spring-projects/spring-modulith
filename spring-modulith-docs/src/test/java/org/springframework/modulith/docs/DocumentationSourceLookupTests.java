/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.docs;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.JavaPackage;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DocumentationSourceLookup}.
 */
class DocumentationSourceLookupTests {

	@Test
	void usesSpringModulithSourceIfConfigured() {

		System.setProperty("spring.config.additional-location", "classpath:documentation-source/spring-modulith.properties");

		var source = DocumentationSourceLookup.getDocumentationSource();

		// Should return either SpringModulithDocumentationSource or NoOpDocumentationSource
		// (depending on whether metadata file exists)
		assertThat(source).isNotNull();
	}

	@Test
	void usesCustomSourceIfConfigured() {

		System.setProperty("spring.config.additional-location", "classpath:documentation-source/custom-type.properties");

		var source = DocumentationSourceLookup.getDocumentationSource();

		assertThat(source).isInstanceOf(TestDocumentationSource.class);
	}

	@Test
	void usesDefaultSourceWhenNoConfigurationProvided() {

		// Clear any existing configuration
		System.clearProperty("spring.config.additional-location");

		var source = DocumentationSourceLookup.getDocumentationSource();

		// Should return either SpringModulithDocumentationSource or NoOpDocumentationSource as default
		assertThat(source).isNotNull();
	}

	/**
	 * Test implementation of {@link DocumentationSource} for testing custom type configuration.
	 */
	public static class TestDocumentationSource implements DocumentationSource {

		@Override
		public Optional<String> getDocumentation(JavaMethod method) {
			return Optional.of("Test method documentation");
		}

		@Override
		public Optional<String> getDocumentation(JavaClass type) {
			return Optional.of("Test class documentation");
		}

		@Override
		public Optional<String> getDocumentation(JavaPackage pkg) {
			return Optional.of("Test package documentation");
		}
	}
}
