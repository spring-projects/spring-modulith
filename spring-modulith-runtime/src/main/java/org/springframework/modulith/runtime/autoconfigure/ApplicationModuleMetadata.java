/*
 * Copyright 2025 the original author or authors.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.util.Assert;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * An abstraction for the data captured in the application module metadata file (typically
 * {@value org.springframework.modulith.core.util.ApplicationModulesExporter#DEFAULT_LOCATION}).
 *
 * @author Oliver Drotbohm
 * @since 1.4
 * @see org.springframework.modulith.core.util.ApplicationModulesExporter#DEFAULT_LOCATION
 */
class ApplicationModuleMetadata {

	private static final ApplicationModuleMetadata NONE = new ApplicationModuleMetadata();

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModuleMetadata.class);

	/**
	 * Creates a new {@link ApplicationModuleMetadata} for the given {@link Resource}.
	 *
	 * @param resource must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModuleMetadata of(Resource resource) {

		Assert.notNull(resource, "Resource must not be null!");

		if (!resource.exists()) {
			LOGGER.debug("Did not find application module metadata in {}.", resource.getDescription());
			return NONE;
		}

		return new ResourceBasedApplicationModuleMetadata(resource);
	}

	/**
	 * Returns whether the metadata is present at all.
	 */
	public boolean isPresent() {
		return false;
	}

	/**
	 * Returns all {@link ApplicationModuleIdentifier}s.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<ApplicationModuleIdentifier> getIdentifiers() {
		return Collections.emptyList();
	}

	/**
	 * Returns the names of the types registered as {@link org.springframework.modulith.ApplicationModuleInitializer}.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<String> getInitializerTypeNames() {
		return Collections.emptyList();
	}

	private static class ResourceBasedApplicationModuleMetadata extends ApplicationModuleMetadata {

		private final DocumentContext document;

		public ResourceBasedApplicationModuleMetadata(Resource metadata) {

			var description = metadata.getDescription();

			Assert.isTrue(metadata.exists(), () -> "Resource %s does not exist!".formatted(description));

			LOGGER.debug("Using application module metadata located in {}.", description);

			try {
				this.document = JsonPath.parse(metadata.getFile());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.runtime.autoconfigure.ApplicationModuleMetadata#isPresent()
		 */
		@Override
		public boolean isPresent() {
			return true;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.runtime.autoconfigure.ApplicationModuleMetadata#getIdentifiers()
		 */
		@Override
		public List<ApplicationModuleIdentifier> getIdentifiers() {

			return document.<Collection<String>> read("$.keys()").stream()
					.map(ApplicationModuleIdentifier::of)
					.toList();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.runtime.autoconfigure.ApplicationModuleMetadata#getInitializerTypeNames()
		 */
		@Override
		public List<String> getInitializerTypeNames() {
			return document.<List<String>> read("$..initializers[*]");
		}
	}
}
