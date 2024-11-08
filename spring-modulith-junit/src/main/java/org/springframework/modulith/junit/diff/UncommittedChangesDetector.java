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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import com.tngtech.archunit.thirdparty.com.google.common.collect.Streams;

/**
 * Implementation to get latest local file changes.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
enum UncommittedChangesDetector implements FileModificationDetector {

	INSTANCE;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.junit.FileModificationDetector#getModifiedFiles()
	 */
	@Override
	public Stream<ModifiedFile> getModifiedFiles() {
		return withRepository(UncommittedChangesDetector::findUncommittedChanges);
	}

	private static Stream<ModifiedFile> findUncommittedChanges(Repository repository) {

		return withTry(() -> new Git(repository), git -> {

			var status = git.status().call();

			return Streams.concat(status.getUncommittedChanges().stream(), status.getUntracked().stream())
					.map(ModifiedFile::new);
		});
	}
}
