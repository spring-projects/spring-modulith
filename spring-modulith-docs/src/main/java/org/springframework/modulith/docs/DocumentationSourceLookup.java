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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

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

		var environment = new StandardEnvironment();
		ConfigDataEnvironmentPostProcessor.applyTo(environment,
				new DefaultResourceLoader(DocumentationSourceLookup.class.getClassLoader()), null);

		var configuredSource = environment.getProperty(DOCUMENTATION_SOURCE_PROPERTY, String.class);

		// Nothing configured? Use SpringFactoriesLoader or fallback
		if (!StringUtils.hasText(configuredSource)) {
			return lookupViaSpringFactoriesOrFallback();
		}

		// Check predefined strategy
		if ("spring-modulith".equals(configuredSource)) {
			return getSpringModulithDocumentationSource();
		}

		// Try to load configured value as class
		try {

			var sourceClass = ClassUtils.forName(configuredSource, DocumentationSource.class.getClassLoader());
			return BeanUtils.instantiateClass(sourceClass, DocumentationSource.class);

		} catch (ClassNotFoundException | LinkageError o_O) {
			throw new IllegalStateException("Unable to load documentation source class: " + configuredSource, o_O);
		}
	}

	/**
	 * Attempts to load documentation source via {@link SpringFactoriesLoader} (deprecated), falling back to the default
	 * source if none found.
	 *
	 * @return will never be {@literal null}.
	 */
	private static DocumentationSource lookupViaSpringFactoriesOrFallback() {

		List<DocumentationSource> loadFactories = SpringFactoriesLoader.loadFactories(DocumentationSource.class,
				DocumentationSource.class.getClassLoader());

		var size = loadFactories.size();

		if (size == 0) {
			return getDefaultDocumentationSource();
		}

		if (size > 1) {
			throw new IllegalStateException(
					"Multiple documentation sources configured via spring.factories. Only one supported! %s"
							.formatted(loadFactories));
		}

		LOG.warn(
				"Configuring documentation source via spring.factories is deprecated! Please configure {} instead.",
				DOCUMENTATION_SOURCE_PROPERTY);

		return loadFactories.get(0);
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
