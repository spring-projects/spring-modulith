/*
 * Copyright 2024-2026 the original author or authors.
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
package org.springframework.modulith.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.junit.diff.FileModificationDetector;
import org.springframework.util.Assert;

/**
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
class StateStore {

	private static final Logger log = LoggerFactory.getLogger(StateStore.class);

	private final Store store;

	/**
	 * Creates a new {@link StateStore} for the given {@link ExtensionContext}.
	 *
	 * @param context must not be {@literal null}.
	 */
	StateStore(ExtensionContext context) {

		Assert.notNull(context, "ExtensionContext must not be null!");

		this.store = context.getRoot().getStore(Namespace.create(ModulithExecutionCondition.class));
	}

	/**
	 * Returns all changes made to the project.
	 *
	 * @return
	 */
	Changes getChanges() {

		return (Changes) store.getOrComputeIfAbsent("changed-files", s -> {

			// Lookup configuration
			var environment = new StandardEnvironment();
			ConfigDataEnvironmentPostProcessor.applyTo(environment);

			if (Boolean.TRUE == environment.getProperty("spring.modulith.test.skip-optimizations", Boolean.class)) {
				return Changes.NONE;
			}

			// Determine detector
			var detector = FileModificationDetector.getDetector(environment);
			var result = Changes.of(detector.getModifiedFiles());

			if (log.isInfoEnabled()) {

				log.trace("Detected changes:");
				log.trace("-----------------");

				result.forEach(it -> log.info(it.toString()));
			}

			// Obtain changes
			return Changes.of(detector.getModifiedFiles());
		});
	}
}
