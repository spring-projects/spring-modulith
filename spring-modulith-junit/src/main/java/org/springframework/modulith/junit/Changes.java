package org.springframework.modulith.junit;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.modulith.junit.Change.JavaClassChange;
import org.springframework.modulith.junit.Change.JavaTestClassChange;
import org.springframework.modulith.junit.Change.OtherFileChange;
import org.springframework.modulith.junit.diff.ModifiedFilePath;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

final class Changes {
	private static final String STANDARD_SOURCE_DIRECTORY = "src/main/java";
	private static final String STANDARD_TEST_SOURCE_DIRECTORY = "src/test/java";

	private Changes() {}

	static Set<Change> toChanges(Set<ModifiedFilePath> modifiedFilePaths) {
		return modifiedFilePaths.stream().map(Changes::toChange).collect(Collectors.toSet());
	}

	static Change toChange(ModifiedFilePath modifiedFilePath) {
		if ("java".equalsIgnoreCase(StringUtils.getFilenameExtension(modifiedFilePath.path()))) {
			String withoutExtension = StringUtils.stripFilenameExtension(modifiedFilePath.path());

			int startOfMainDir = withoutExtension.indexOf(STANDARD_SOURCE_DIRECTORY);
			int startOfTestDir = withoutExtension.indexOf(STANDARD_TEST_SOURCE_DIRECTORY);

			if (startOfTestDir > 0 && (startOfMainDir < 0 || startOfTestDir < startOfMainDir)) {
				String fullyQualifiedClassName = ClassUtils.convertResourcePathToClassName(
						withoutExtension.substring(startOfTestDir + STANDARD_TEST_SOURCE_DIRECTORY.length() + 1));

				return new JavaTestClassChange(fullyQualifiedClassName);
			} else if (startOfMainDir > 0 && (startOfTestDir < 0 || startOfMainDir < startOfTestDir)) {
				String fullyQualifiedClassName = ClassUtils.convertResourcePathToClassName(
						withoutExtension.substring(startOfMainDir + STANDARD_SOURCE_DIRECTORY.length() + 1));

				return new JavaClassChange(fullyQualifiedClassName);
			} else {
				// This is unusual, fall back to just assume that the full path is the package -> TODO At least log this
				String fullyQualifiedClassName = ClassUtils.convertResourcePathToClassName(withoutExtension);

				return new JavaClassChange(fullyQualifiedClassName);
			}
		} else {
			// TODO Do these need to be relative to the module root (i.e. where src/main/java etc. reside)?
			return new OtherFileChange(modifiedFilePath.path());
		}
	}
}
