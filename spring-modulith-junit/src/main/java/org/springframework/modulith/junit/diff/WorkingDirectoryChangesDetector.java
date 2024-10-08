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

import java.io.File;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * A {@link FileModificationDetector} that filters the {@link ModifiedFile} instances returned by a delegate
 * {@link FileModificationDetector} to only contain those nested in the current, repository-relative working directory.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class WorkingDirectoryChangesDetector implements FileModificationDetector {

	private final FileModificationDetector delegate;
	private final String workingDirectory;

	/**
	 * Creates a new {@link WorkingDirectoryChangesDetector} for the given {@link FileModificationDetector} delegate and
	 * directory;
	 *
	 * @param delegate must not be {@literal null}.
	 * @param workingDirectory must not be {@literal null}.
	 */
	WorkingDirectoryChangesDetector(FileModificationDetector delegate, String workingDirectory) {

		Assert.notNull(delegate, "FileModificationDetector must not be null!");
		Assert.notNull(workingDirectory, "Working folder must not be null!");

		this.delegate = delegate;
		this.workingDirectory = workingDirectory;
	}

	/**
	 * Creates a new {@link WorkingDirectoryChangesDetector} for the current Git repository-relative working directory.
	 *
	 * @param delegate must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static WorkingDirectoryChangesDetector of(FileModificationDetector delegate) {

		var pathToRepo = JGitUtil.withRepository(it -> it.getDirectory().getParent());

		// someFolder/.
		var currentWorkingDirectory = new File(".").getAbsolutePath();

		// Strip repository base and /.
		var repositoryRelative = currentWorkingDirectory.substring(pathToRepo.length() + 1,
				currentWorkingDirectory.length() - 2);

		return new WorkingDirectoryChangesDetector(delegate, repositoryRelative);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.junit.diff.FileModificationDetector#getModifiedFiles()
	 */
	@Override
	public Stream<ModifiedFile> getModifiedFiles() {

		return delegate.getModifiedFiles()
				.filter(it -> it.path().startsWith(workingDirectory))
				.map(it -> it.asRelativeTo(workingDirectory));
	}
}
