package org.springframework.modulith.junit.diff;

import static org.springframework.modulith.junit.ModulithExecutionExtension.*;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertyResolver;
import org.springframework.lang.NonNull;

/**
 * Implementation to get changes between HEAD and a complete or abbreviated SHA-1 or other revision, like
 * <code>HEAD~2</code>. See {@link org.eclipse.jgit.lib.Repository#resolve(String)} for more information.
 */
public class ReferenceCommitDetector implements FileModificationDetector {
	private static final Logger log = LoggerFactory.getLogger(ReferenceCommitDetector.class);

	@Override
	public @NonNull Set<ModifiedFilePath> getModifiedFiles(@NonNull PropertyResolver propertyResolver)
			throws IOException {
		String commitIdToCompareTo = getReferenceCommitProperty(propertyResolver);

		try (var repo = JGitUtil.buildRepository()) {
			String compareTo;
			if (commitIdToCompareTo == null || commitIdToCompareTo.isEmpty()) {
				log.warn("No reference-commit configured, comparing to HEAD~1.");
				compareTo = "HEAD~1";
			} else {
				log.info("Comparing to git commit #{}", commitIdToCompareTo);
				compareTo = commitIdToCompareTo;
			}

			String localBranch = repo.getFullBranch();
			Stream<DiffEntry> diffs = JGitUtil.diffRefs(repo, compareTo, localBranch);

			return JGitUtil.convertDiffEntriesToFileChanges(diffs).collect(Collectors.toSet());
		}
	}

	public static String getReferenceCommitProperty(PropertyResolver propertyResolver) {
		return propertyResolver.getProperty(CONFIG_PROPERTY_PREFIX + ".reference-commit");
	}
}
