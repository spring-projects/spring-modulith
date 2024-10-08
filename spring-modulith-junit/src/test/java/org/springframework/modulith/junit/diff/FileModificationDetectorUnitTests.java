/*
 * Copyright 2024 the original author or authors.
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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link FileModificationDetector}.
 *
 * @author Oliver Drotbohm
 */
class FileModificationDetectorUnitTests {

	@Test // GH-31
	void usesReferenceCommitDetectionIfHashConfigured() {
		assertDetector(ReferenceCommitDetector.class, null, "HEAD^");
	}

	@Test // GH-31
	void usesReferenceCommitDetectionIfConfiguredExplicitly() {
		assertDetector(ReferenceCommitDetector.class, "reference-commit", null);
	}

	@Test // GH-31
	void usesUncommittedChangesIfConfiguredExplicitly() {
		assertDetector(UncommittedChangesDetector.class, "uncommitted-changes", null);
	}

	@Test // GH-31
	void registersCustomDetectorByType() {

		var customDetector = CustomFileModificationDetector.class;

		assertDetector(customDetector, customDetector.getName(), null);
	}

	@Test // GH-31
	void selectingDefaultExplicitlyUsesDefault() {

		var explicitDetector = FileModificationDetector.getTargetDetector(setupEnvironment("default", null));

		assertThat(FileModificationDetector.getTargetDetector(setupEnvironment(null, null)))
				.isEqualTo(explicitDetector);
	}

	@Test // GH-31
	void rejectsInvalidDetectorName() {

		assertThatIllegalStateException().isThrownBy(() -> {
			FileModificationDetector.getDetector(setupEnvironment("some.Garbage", null));
		});
	}

	private static void assertDetector(Class<?> expected, String detector, String referenceCommit) {

		var environment = setupEnvironment(detector, referenceCommit);

		assertThat(FileModificationDetector.getTargetDetector(environment)).isInstanceOf(expected);
	}

	private static Environment setupEnvironment(String detector, String referenceCommit) {

		var environment = new MockEnvironment();

		if (detector != null) {
			environment.setProperty("spring.modulith.test.file-modification-detector", detector);
		}

		if (referenceCommit != null) {
			environment.setProperty("spring.modulith.test.reference-commit", referenceCommit);
		}

		return environment;
	}

	static class CustomFileModificationDetector implements FileModificationDetector {

		@Override
		public Stream<ModifiedFile> getModifiedFiles() {
			return Stream.empty();
		}
	}
}
