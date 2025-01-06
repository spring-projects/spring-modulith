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
package org.springframework.modulith.junit.diff;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.PropertyResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * SPI to plug different strategies of how to find the files currently modified in a project.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
public interface FileModificationDetector {

	public static final String CONFIG_PROPERTY_PREFIX = "spring.modulith.test";
	static final Logger log = LoggerFactory.getLogger(FileModificationDetector.class);

	/**
	 * Returns all {@link ModifiedFile}s detected.
	 *
	 * @return will never be {@literal null}.
	 */
	Stream<ModifiedFile> getModifiedFiles();

	/**
	 * Returns the {@link FileModificationDetector} to be used.
	 *
	 * @param propertyResolver must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static FileModificationDetector getDetector(PropertyResolver propertyResolver) {
		return WorkingDirectoryChangesDetector.of(getTargetDetector(propertyResolver));
	}

	static FileModificationDetector getTargetDetector(PropertyResolver propertyResolver) {

		Assert.notNull(propertyResolver, "PropertyResolver must not be null!");

		var detectorSelector = propertyResolver.getProperty(CONFIG_PROPERTY_PREFIX + ".file-modification-detector");
		var referenceCommit = ReferenceCommitDetector.getReferenceCommitProperty(propertyResolver);

		if (!StringUtils.hasText(detectorSelector)) {
			return getDefaultDetector(referenceCommit);
		}

		return switch (detectorSelector) {

			case "uncommitted-changes" -> UncommittedChangesDetector.INSTANCE;
			case "reference-commit" -> new ReferenceCommitDetector(referenceCommit);
			case "default" -> getDefaultDetector(referenceCommit);
			default -> {

				try {

					var detectorType = ClassUtils.forName(detectorSelector, FileModificationDetector.class.getClassLoader());

					log.info("Found request via property for file modification detector '{}'", detectorSelector);

					yield BeanUtils.instantiateClass(detectorType, FileModificationDetector.class);

				} catch (ClassNotFoundException | LinkageError o_O) {
					throw new IllegalStateException(o_O);
				}
			}
		};
	}

	/**
	 * Returns a {@link ReferenceCommitDetector} if the given reference commit is not empty.
	 *
	 * @param referenceCommit can be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	private static FileModificationDetector getDefaultDetector(@Nullable String referenceCommit) {

		if (StringUtils.hasText(referenceCommit)) {
			return new ReferenceCommitDetector(referenceCommit);
		}

		log.info("Using default file modification detector (uncommitted and unpushed changes):");

		return () -> Stream.of(UncommittedChangesDetector.INSTANCE, UnpushedCommitsDetector.INSTANCE)
				.flatMap(it -> it.getModifiedFiles());
	}
}
