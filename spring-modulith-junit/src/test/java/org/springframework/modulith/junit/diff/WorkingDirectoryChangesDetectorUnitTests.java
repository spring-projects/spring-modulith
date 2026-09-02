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

		registerModifications("rootPom.xml", "nested/nestedPom.xml");

		var detector = new WorkingDirectoryChangesDetector(delegate, "nested");

		assertThat(detector.getModifiedFiles())
				.extracting(ModifiedFile::path)
				.containsExactly("nestedPom.xml");
	}

	@Test // GH-1849
	void resolvesRepositoryRelativeWorkingDirectoryForNestedMavenModule(@TempDir Path temp) throws Exception {

		initGitRepository(temp, "root-pom.xml", "nested-module/module-pom.xml");
		registerModifications("root-pom.xml", "nested-module/module-pom.xml");

		var detector = new WorkingDirectoryChangesDetector(delegate, "nested-module");

		assertThat(detector.getModifiedFiles())
				.extracting(ModifiedFile::path)
				.containsExactly("module-pom.xml");
	}

	@Test // GH-1849
	void resolvesRepositoryRelativeWorkingDirectoryForGitSubmoduleWorkTree(@TempDir Path temp) throws Exception {

		initSubmoduleStyleRepository(temp);

		registerModifications("README", "module-a/module-a-pom.xml", "module-b/module-b-pom.xml");

		var detector = new WorkingDirectoryChangesDetector(delegate, "module-a");

		assertThat(detector.getModifiedFiles())
				.extracting(ModifiedFile::path)
				.containsExactly("module-a-pom.xml");
	}

	private void registerModifications(String... files) {
		when(delegate.getModifiedFiles()).thenReturn(Stream.of(files).map(ModifiedFile::new));
	}

	private static void initGitRepository(Path root, String... files) throws Exception {

		for (var file : files) {

			var path = root.resolve(file);

			Files.createDirectories(path.getParent());
			Files.writeString(path, file);
		}

		try (Git git = Git.init().setDirectory(root.toFile()).call()) {

			git.add().addFilepattern(".").call();
			git.commit().setSign(false).setMessage("init").call();
		}
	}

	/**
	 * Sets up a work tree resembling a Git submodule, i.e. one whose {@code .git} entry is a file pointing to the actual
	 * Git metadata directory located outside the work tree (e.g. in the parent repository's {@code .git/modules}), rather
	 * than a {@code .git} directory living right inside the work tree.
	 */
	private static void initSubmoduleStyleRepository(Path temp) throws Exception {

		var parentRepository = temp.resolve("parent-repository");
		var gitDir = parentRepository.resolve(".git/modules/example-library");
		var workTree = temp.resolve("example-library");

		Files.createDirectories(gitDir);
		Files.createDirectories(workTree.resolve("module-a"));
		Files.createDirectories(workTree.resolve("module-b"));

		Git.init().setDirectory(gitDir.toFile()).setBare(true).call();

		Files.writeString(workTree.resolve("README"), "example");
		Files.writeString(workTree.resolve("module-a/module-a-pom.xml"), "module-a");
		Files.writeString(workTree.resolve("module-b/module-b-pom.xml"), "module-b");
		Files.writeString(workTree.resolve(".git"), "gitdir: " + gitDir.toAbsolutePath() + "\n");

		try (Repository repository = new FileRepositoryBuilder()
				.setGitDir(gitDir.toFile())
				.setWorkTree(workTree.toFile())
				.build(); Git git = new Git(repository)) {

			git.add().addFilepattern(".").call();
			git.commit().setSign(false).setMessage("init").call();
		}
	}
}
