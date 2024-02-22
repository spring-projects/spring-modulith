package org.springframework.modulith.junit.diff;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.core.env.PropertyResolver;
import org.springframework.lang.NonNull;

import com.tngtech.archunit.thirdparty.com.google.common.collect.Streams;

/**
 * Implementation to get latest local file changes.
 *
 * @author Lukas Dohmen
 */
public class UncommittedChangesDetector implements FileModificationDetector {

	@Override
	public @NonNull Set<ModifiedFilePath> getModifiedFiles(@NonNull PropertyResolver propertyResolver)
			throws IOException {

		try (var repo = new FileRepositoryBuilder().findGitDir().build()) {
			return findUncommittedChanges(repo).collect(Collectors.toSet());
		}
	}

	private static Stream<ModifiedFilePath> findUncommittedChanges(Repository repository) throws IOException {
		try (Git git = new Git(repository)) {
			Status status = git.status().call();

			return Streams.concat(status.getUncommittedChanges().stream(), status.getUntracked().stream())
					.map(ModifiedFilePath::new);
		} catch (GitAPIException e) {
			throw new IOException("Unable to find uncommitted changes", e);
		}
	}

}
