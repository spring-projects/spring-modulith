/*
 * Copyright 2024 the original author or authors.
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

import static org.springframework.modulith.junit.diff.JGitUtil.*;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertyResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Implementation to get changes between HEAD and a complete or abbreviated SHA-1 or other revision, like
 * <code>HEAD~2</code>. See {@link org.eclipse.jgit.lib.Repository#resolve(String)} for more information.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
class ReferenceCommitDetector implements FileModificationDetector {

	private static final Logger log = LoggerFactory.getLogger(ReferenceCommitDetector.class);
	private static final String REFERENCE_COMMIT_PROPERTY = CONFIG_PROPERTY_PREFIX + ".reference-commit";

	private final String referenceCommit;

	/**
	 * Creates a new {@link ReferenceCommitDetector} for the given reference commit.
	 *
	 * @param referenceCommit can be {@literal null}.
	 */
	ReferenceCommitDetector(@Nullable String referenceCommit) {

		if (StringUtils.hasText(referenceCommit)) {
			log.info("Comparing to git commit {}", referenceCommit);
			this.referenceCommit = referenceCommit;
		} else {
			log.warn("No reference-commit configured, comparing to HEAD.");
			this.referenceCommit = "HEAD";
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.junit.FileModificationDetector#getModifiedFiles()
	 */
	@Override
	public Stream<ModifiedFile> getModifiedFiles() {

		return withRepository(repo -> {

			var localBranch = repo.getFullBranch();
			return toModifiedFiles(repo, referenceCommit, localBranch);
		});
	}

	@Nullable
	public static String getReferenceCommitProperty(PropertyResolver propertyResolver) {
		return propertyResolver.getProperty(REFERENCE_COMMIT_PROPERTY);
	}
}
