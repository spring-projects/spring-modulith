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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.core.config.StrategyLookup;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A factory for the {@link DocumentationSource} to be used when generating documentation.
 */
class DocumentationSourceLookup {

	private static final String DOCUMENTATION_SOURCE_PROPERTY = "spring.modulith.documentation-source";
	private static final Logger LOG = LoggerFactory.getLogger(DocumentationSourceLookup.class);

	/**
	 * Returns the {@link DocumentationSource} to be used for documentation generation. Will use the following
	 * algorithm:
	 * <ol>
	 * <li>Use the predefined strategy if {@code spring-modulith} is configured for the
	 * {@code spring.modulith.documentation-source} configuration property.</li>
	 * <li>Interpret the configured value as class if it doesn't match the predefined value.</li>
	 * <li>Use the {@link DocumentationSource} declared in {@code META-INF/spring.factories} (deprecated)</li>
	 * <li>A final fallback on {@link SpringModulithDocumentationSource} or {@link NoOpDocumentationSource} if the
	 * metadata file is not available.</li>
	 * </ol>
	 *
	 * @return will never be {@literal null}.
	 */
	static DocumentationSource getDocumentationSource() {

		Map<String, Supplier<DocumentationSource>> predefinedStrategies = Map.of(
				"spring-modulith", DocumentationSourceLookup::getSpringModulithDocumentationSource);

		var lookup = new StrategyLookup<>(
				DOCUMENTATION_SOURCE_PROPERTY,
				DocumentationSource.class,
				predefinedStrategies,
				DocumentationSourceLookup::getDefaultDocumentationSource);

		return lookup.lookup();
	}

	/**
	 * Returns the Spring Modulith documentation source, or a no-op source if metadata is not available.
	 *
	 * @return will never be {@literal null}.
	 */
	private static DocumentationSource getSpringModulithDocumentationSource() {

		return SpringModulithDocumentationSource.getInstance()
				.map(it -> {
					LOG.debug("Using Javadoc extracted by Spring Modulith in {}.",
							SpringModulithDocumentationSource.getMetadataLocation());
					return it;
				})
				.orElseGet(NoOpDocumentationSource::new);
	}

	/**
	 * Returns the default documentation source (Spring Modulith or no-op).
	 *
	 * @return will never be {@literal null}.
	 */
	private static DocumentationSource getDefaultDocumentationSource() {
		return getSpringModulithDocumentationSource();
	}
}
