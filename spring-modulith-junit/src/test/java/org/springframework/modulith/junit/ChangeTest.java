package org.springframework.modulith.junit;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.junit.Change.JavaClassChange;
import org.springframework.modulith.junit.Change.JavaTestClassChange;
import org.springframework.modulith.junit.Change.OtherFileChange;
import org.springframework.modulith.junit.diff.ModifiedFilePath;

class ChangesTest {
	@Test
	void shouldInterpredModifiedFilePathsCorrectly() {
		// given
		Set<ModifiedFilePath> modifiedFilePaths = Set.of(
				new ModifiedFilePath("spring-modulith-junit/src/main/java/org/springframework/modulith/Changes.java"),
				new ModifiedFilePath("spring-modulith-junit/src/test/java/org/springframework/modulith/ChangesTest.java"),
				new ModifiedFilePath(
						"spring-modulith-junit/src/main/resources/META-INF/additional-spring-configuration-metadata.json"));

		// when
		Set<Change> result = Changes.toChanges(modifiedFilePaths);

		// then
		assertThat(result).containsExactlyInAnyOrder(
				new JavaClassChange("org.springframework.modulith.Changes"),
				new JavaTestClassChange("org.springframework.modulith.ChangesTest"),
				new OtherFileChange(
						"spring-modulith-junit/src/main/resources/META-INF/additional-spring-configuration-metadata.json"));
	}
}
