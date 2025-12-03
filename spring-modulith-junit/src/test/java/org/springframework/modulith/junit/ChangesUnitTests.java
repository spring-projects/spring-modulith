/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.junit;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.modulith.junit.Changes.Change.JavaSourceChange;
import org.springframework.modulith.junit.Changes.Change.JavaTestSourceChange;
import org.springframework.modulith.junit.Changes.Change.KotlinSourceChange;
import org.springframework.modulith.junit.Changes.Change.KotlinTestSourceChange;
import org.springframework.modulith.junit.Changes.Change.OtherFileChange;
import org.springframework.modulith.junit.Changes.OnNoChange;
import org.springframework.modulith.junit.diff.ModifiedFile;

/**
 * Unit tests for {@link Changes}.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 * @author Valentin Bossi
 */
class ChangesUnitTests {

	@TestFactory // GH-31
	Stream<DynamicTest> detectsClasspathFileChange() {

		var files = Stream.of("src/main/resources/some.txt", "src/test/resources/some.txt");

		return DynamicTest.stream(files, it -> it + " is considered classpath resource", it -> {
			assertThat(new OtherFileChange(it).isClasspathResource()).isTrue();
		});
	}

	@TestFactory // GH-31
	Stream<DynamicTest> detectsNonClasspathFileChange() {

		var files = Stream.of(

				// Maven
				"pom.xml",

				// Gradle
				"build.gradle", "build.gradle.kts", "gradle.properties", "settings.gradle", "settings.gradle.kts");

		return DynamicTest.stream(files, it -> it + " is considered build resource", it -> {

			var change = new OtherFileChange(it);

			assertThat(change.isClasspathResource()).isFalse();
			assertThat(change.affectsBuildResource()).isTrue();
		});
	}

	@Test // GH-31, GH-1382
	void shouldInterpredModifiedFilePathsCorrectly() {

		// given
		var modifiedFilePaths = Stream.of(
				"src/main/java/org/springframework/modulith/junit/Changes.java",
				"src/test/java/org/springframework/modulith/ChangesTest.java",
				"src/test/kotlin/org/springframework/modulith/KotlinServiceTest.kt",
				"src/main/kotlin/org/springframework/modulith/KotlinService.kt",
				"src/main/resources/META-INF/additional-spring-configuration-metadata.json")
				.map(ModifiedFile::new);

		// when
		var result = Changes.of(modifiedFilePaths, OnNoChange.EXECUTE_ALL);

		// then
		assertThat(result.hasClasspathResourceChange()).isTrue();
		assertThat(result).containsExactlyInAnyOrder(
				new JavaSourceChange("org.springframework.modulith.junit.Changes"),
				new JavaTestSourceChange("org.springframework.modulith.ChangesTest"),
				new KotlinTestSourceChange("org.springframework.modulith.KotlinServiceTest"),
				new KotlinSourceChange("org.springframework.modulith.KotlinService"),
				new OtherFileChange("src/main/resources/META-INF/additional-spring-configuration-metadata.json"));
	}
}
