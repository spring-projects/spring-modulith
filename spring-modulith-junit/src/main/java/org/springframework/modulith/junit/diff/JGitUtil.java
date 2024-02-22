package org.springframework.modulith.junit.diff;

import java.io.IOException;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Utility to contain re-used JGit operations. For internal use only.
 */
final class JGitUtil {
	private JGitUtil() {}

	static Stream<ModifiedFilePath> convertDiffEntriesToFileChanges(Stream<DiffEntry> diffEntries) {
		return diffEntries.flatMap(
				entry -> Stream.of(new ModifiedFilePath(entry.getNewPath()), new ModifiedFilePath(entry.getOldPath())))
				.filter(change -> !change.path().equals("/dev/null"));
	}

	static Repository buildRepository() throws IOException {
		return new FileRepositoryBuilder().findGitDir().build();
	}

	static Stream<DiffEntry> diffRefs(Repository repository, String oldRef, String newRef) throws IOException {
		try (Git git = new Git(repository)) {
			AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, oldRef);
			AbstractTreeIterator newTreeParser = prepareTreeParser(repository, newRef);

			return git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call().stream();
		} catch (GitAPIException e) {
			throw new IOException("Unable to find diff between refs '%s' and '%s'".formatted(oldRef, newRef), e);
		}
	}

	private static AbstractTreeIterator prepareTreeParser(Repository repository, String ref) throws IOException {
		ObjectId commitId = repository.resolve(ref);

		try (RevWalk walk = new RevWalk(repository)) {
			RevCommit commit = walk.parseCommit(commitId);
			RevTree tree = walk.parseTree(commit.getTree().getId());

			CanonicalTreeParser treeParser = new CanonicalTreeParser();
			try (ObjectReader reader = repository.newObjectReader()) {
				treeParser.reset(reader, tree.getId());
			}

			return treeParser;
		}
	}
}
