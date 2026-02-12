/*
 * Copyright 2025-2026 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.modulith.junit.Changes.OnNoChange;
import org.springframework.modulith.junit.diff.FileModificationDetector;
import org.springframework.util.Assert;

/**
 * Dedicated factory to create a {@link Changes} instance from an {@link Environment}.
 *
 * @author Oliver Drotbohm
 * @author Valentin Bossi
 * @since 2.1
 */
class ChangesFactory {

	private static final Logger log = LoggerFactory.getLogger(StateStore.class);

	/**
	 * Creates a new {@link Changes} instance from an {@link Environment}.
	 *
	 * @param environment will never be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static Changes getChanges(Environment environment) {

		Assert.notNull(environment, "Environment must not be null!");

		if (Boolean.TRUE == environment.getProperty("spring.modulith.test.skip-optimizations", Boolean.class)) {
			return Changes.NONE;
		}

		var onNoChanges = OnNoChange.fromConfig(environment.getProperty("spring.modulith.test.on-no-changes"));

		// Determine detector
		var detector = FileModificationDetector.getDetector(environment);
		var result = Changes.of(detector.getModifiedFiles(), onNoChanges);

		if (log.isInfoEnabled()) {

			log.trace("Detected changes:");
			log.trace("-----------------");

			result.forEach(it -> log.info(it.toString()));
		}

		// Obtain changes
		return result;
	}
}
