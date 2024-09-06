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

import org.eclipse.jgit.lib.BranchConfig;

/**
 * <p>
 * Find all changes that have not been pushed to the remote branch yet.
 * <p>
 * To be precise, this finds the diff between the local HEAD and its tracking branch and the uncommitted and untracked
 * changes. <em>Note:</em> This will not fetch from the remote first!
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
enum UnpushedCommitsDetector implements FileModificationDetector {

	INSTANCE;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.junit.FileModificationDetector#getModifiedFiles()
	 */
	@Override
	public Stream<ModifiedFile> getModifiedFiles() {

		return withRepository(repo -> {

			var localBranch = repo.getFullBranch();
			var trackingBranch = new BranchConfig(repo.getConfig(), repo.getBranch()).getTrackingBranch();

			return localBranch != null && trackingBranch != null
					? toModifiedFiles(repo, localBranch, trackingBranch)
					: Stream.empty();
		});
	}
}
