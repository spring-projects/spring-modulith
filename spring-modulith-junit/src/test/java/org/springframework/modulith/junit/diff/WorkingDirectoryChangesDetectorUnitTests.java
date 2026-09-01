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
package org.springframework.modulith.junit.diff;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkingDirectoryChangesDetector}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
@ExtendWith(MockitoExtension.class)
class WorkingDirectoryChangesDetectorUnitTests {

	@Mock FileModificationDetector delegate;

	@Test // GH-861
	void filtersFilesContainedInReferenceFolder() {

		when(delegate.getModifiedFiles())
				.thenReturn(Stream.of("rootPom.xml", "nested/nestedPom.xml").map(ModifiedFile::new));

		var detector = new WorkingDirectoryChangesDetector(delegate, "nested");

		assertThat(detector.getModifiedFiles())
				.extracting(ModifiedFile::path)
				.containsExactly("nestedPom.xml");
	}

	@Test
	void resolvesRepositoryRelativeWorkingDirectoryForNestedMavenModule() {

		var repositoryRoot = "/tmp/example-repository";
		var nestedModule = repositoryRoot + "/nested-module";

		assertThat(WorkingDirectoryChangesDetector.repositoryRelativeWorkingDirectory(repositoryRoot, nestedModule))
				.isEqualTo("nested-module");
	}

	@Test // GH-1849
	void resolvesRepositoryRelativeWorkingDirectoryForGitSubmoduleWorkTree() {

		var workTree = "/tmp/parent-repository/example-library";
		var gitMetadataDirectory = "/tmp/parent-repository/.git/modules/example-library";
		var nestedModule = workTree + "/example-library-core";

		assertThat(nestedModule).doesNotStartWith(gitMetadataDirectory);

		assertThat(WorkingDirectoryChangesDetector.repositoryRelativeWorkingDirectory(workTree, nestedModule))
				.isEqualTo("example-library-core");
	}

	@Test // GH-1849
	void filtersModifiedFilesForGitSubmoduleWorkTree(@TempDir Path temp) throws Exception {

		var nestedModule = initSubmoduleStyleRepository(temp);

		when(delegate.getModifiedFiles())
				.thenReturn(Stream.of("README", "module-a/pom.xml", "module-b/pom.xml").map(ModifiedFile::new));

		var repositoryRelative = WorkingDirectoryChangesDetector.repositoryRelativeWorkingDirectory(
				nestedModule.getParent().toAbsolutePath().toString(), nestedModule.toAbsolutePath().toString());

		var detector = new WorkingDirectoryChangesDetector(delegate, repositoryRelative);

		assertThat(detector.getModifiedFiles())
				.extracting(ModifiedFile::path)
				.containsExactly("pom.xml");
	}

	@Test // GH-1849
	void usesWorkTreeWhenResolvingRepositoryRoot(@TempDir Path temp) throws Exception {

		var nestedModule = initSubmoduleStyleRepository(temp);
		var workTree = nestedModule.getParent();
		var gitDir = workTree.getParent().resolve("parent-repository/.git/modules/example-library");

		try (Repository repository = new FileRepositoryBuilder()
				.setGitDir(gitDir.toFile())
				.setWorkTree(workTree.toFile())
				.build()) {

			assertThat(repository.getWorkTree().getAbsolutePath()).isEqualTo(workTree.toAbsolutePath().toString());
			assertThat(repository.getDirectory().getParent()).isNotEqualTo(repository.getWorkTree().getAbsolutePath());
		}
	}

	private static Path initSubmoduleStyleRepository(Path temp) throws Exception {

		var parentRepository = temp.resolve("parent-repository");
		var gitDir = parentRepository.resolve(".git/modules/example-library");
		var workTree = temp.resolve("example-library");
		var nestedModule = workTree.resolve("module-a");

		Files.createDirectories(gitDir);
		Files.createDirectories(nestedModule);
		Files.createDirectories(workTree.resolve("module-b"));

		Git.init().setDirectory(gitDir.toFile()).setBare(true).call();

		Files.writeString(workTree.resolve("README"), "example");
		Files.writeString(workTree.resolve("module-a/pom.xml"), "module-a");
		Files.writeString(workTree.resolve("module-b/pom.xml"), "module-b");
		Files.writeString(workTree.resolve(".git"), "gitdir: " + gitDir.toAbsolutePath() + "\n");

		Repository repository = new FileRepositoryBuilder()
				.setGitDir(gitDir.toFile())
				.setWorkTree(workTree.toFile())
				.build();

		try (Git git = new Git(repository)) {

			git.add().addFilepattern(".").call();
			git.commit().setMessage("init").call();
		}

		return nestedModule;
	}
}
