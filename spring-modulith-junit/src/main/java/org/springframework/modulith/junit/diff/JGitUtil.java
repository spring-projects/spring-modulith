package org.springframework.modulith.junit.diff;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.util.function.ThrowingFunction;
import org.springframework.util.function.ThrowingSupplier;

/**
 * Utility to contain re-used JGit operations. For internal use only.
 */
interface JGitUtil {

	static <T> T withTry(ThrowingSupplier<T> supplier) {

		try {
			return supplier.get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static <A extends AutoCloseable, T> T withTry(Supplier<A> closable, ThrowingFunction<A, T> supplier) {

		try (A a = closable.get()) {
			return supplier.apply(a);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static <T> T withRepository(ThrowingFunction<Repository, T> function) {
		return withTry(JGitUtil::buildRepository, function);
	}

	static Stream<ModifiedFile> toModifiedFiles(Repository repository, String oldRef, String newRef) {

		try (Git git = new Git(repository)) {

			var oldTreeParser = prepareTreeParser(repository, oldRef);
			var newTreeParser = prepareTreeParser(repository, newRef);

			return git.diff()
					.setOldTree(oldTreeParser)
					.setNewTree(newTreeParser)
					.call().stream()
					.flatMap(entry -> ModifiedFile.of(entry.getNewPath(), entry.getOldPath()))
					.distinct()
					.filter(change -> !change.path().equals("/dev/null"));

		} catch (GitAPIException e) {
			throw new RuntimeException("Unable to find diff between refs '%s' and '%s'".formatted(oldRef, newRef), e);
		}
	}

	private static Repository buildRepository() {
		return withTry(() -> new FileRepositoryBuilder().findGitDir().build());
	}

	private static AbstractTreeIterator prepareTreeParser(Repository repository, String ref) {

		return withTry(() -> new RevWalk(repository), walk -> {

			var commitId = repository.resolve(ref);
			var commit = walk.parseCommit(commitId);
			var tree = walk.parseTree(commit.getTree().getId());
			var treeParser = new CanonicalTreeParser();

			try (ObjectReader reader = repository.newObjectReader()) {
				treeParser.reset(reader, tree.getId());
			}

			return treeParser;
		});
	}
}
