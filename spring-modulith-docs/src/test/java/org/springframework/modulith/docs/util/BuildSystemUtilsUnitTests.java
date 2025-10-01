/*
 * Copyright 2025 the original author or authors.
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
package org.springframework.modulith.docs.util;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Unit tests for {@link BuildSystemUtils}.
 *
 * @author Oliver Drotbohm
 */
class BuildSystemUtilsUnitTests {

	@TestFactory // GH-1386
	Stream<DynamicTest> detectsMavenTargetResources() {

		var values = getSampleResources("target/test-classes");

		return DynamicTest.stream(values, it -> it + " is a test resource", it -> {
			assertThat(BuildSystemUtils.pointsToTestTarget(it)).isTrue();
		});
	}

	@TestFactory // GH-1386
	Stream<DynamicTest> detectsGradleTargetResources() {

		var values = getSampleResources(
				"build/classes/java/test",
				"build/classes/kotlin/test",
				"build/tmp/kapt3/classes/testFixtures");

		return DynamicTest.stream(values, it -> it + " is a test resource", it -> {
			assertThat(BuildSystemUtils.pointsToGradleTestTarget(it)).isTrue();
		});
	}

	private Stream<String> getSampleResources(String... paths) {
		return Stream.of(paths).map(it -> it.concat("/some-test-resource.txt"));
	}
}
