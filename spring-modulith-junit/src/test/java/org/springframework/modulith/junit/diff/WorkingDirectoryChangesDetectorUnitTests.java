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
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

		when(delegate.getModifiedFiles())
				.thenReturn(Stream.of("rootPom.xml", "nested/nestedPom.xml").map(ModifiedFile::new));

		var detector = new WorkingDirectoryChangesDetector(delegate, "nested");

		assertThat(detector.getModifiedFiles())
				.extracting(ModifiedFile::path)
				.containsExactly("nestedPom.xml");
	}
}
