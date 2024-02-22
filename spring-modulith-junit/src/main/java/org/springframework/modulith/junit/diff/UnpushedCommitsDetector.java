package org.springframework.modulith.junit.diff;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.BranchConfig;
import org.springframework.core.env.PropertyResolver;
import org.springframework.lang.NonNull;

/**
 * <p>
 * Find all changes that have not been pushed to the remote branch yet.
 * <p>
 * To be precise, this finds the diff between the local HEAD and its tracking branch and the uncommitted and untracked
 * changes. <em>Note:</em> This will not fetch from the remote first!
 */
public class UnpushedCommitsDetector implements FileModificationDetector {

	@Override
	public @NonNull Set<ModifiedFilePath> getModifiedFiles(@NonNull PropertyResolver propertyResolver)
			throws IOException {
		try (var repo = JGitUtil.buildRepository()) {
			String localBranch = repo.getFullBranch();
			String trackingBranch = new BranchConfig(repo.getConfig(), repo.getBranch()).getTrackingBranch();

			Stream<DiffEntry> diff = localBranch != null && trackingBranch != null
					? JGitUtil.diffRefs(repo, localBranch, trackingBranch)
					: Stream.empty();

			HashSet<ModifiedFilePath> result = new HashSet<>();
			result.addAll(new UncommittedChangesDetector().getModifiedFiles(propertyResolver));
			result.addAll(JGitUtil.convertDiffEntriesToFileChanges(diff).collect(Collectors.toSet()));
			return result;
		}
	}

}
