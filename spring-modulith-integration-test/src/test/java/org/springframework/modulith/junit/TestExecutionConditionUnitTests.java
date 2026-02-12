/*
 * Copyright 2024-2026 the original author or authors.
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

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.springframework.modulith.junit.Changes.OnNoChange;
import org.springframework.modulith.junit.TestExecutionCondition.ConditionContext;
import org.springframework.modulith.junit.diff.ModifiedFile;

import com.acme.myproject.ModulithTest;
import com.acme.myproject.moduleD.ModuleDTest;

/**
 * Unit tests for {@link TestExecutionCondition}.
 *
 * @author Oliver Drotbohm
 */
class TestExecutionConditionUnitTests {

	TestExecutionCondition condition = new TestExecutionCondition();

	@Test // GH-31
	void enablesForSourceChangeInSameModulen() {
		assertEnabled(ModuleDTest.class, "moduleD/SomeConfigurationD.java");
	}

	@Test // GH-31
	void enablesForSourceChangeInModuleDirectlyDependedOn() {
		assertEnabled(ModuleDTest.class, "moduleC/ServiceComponentC.java");
	}

	@Test // GH-31
	void enablesForSourceChangeInModuleIndirectlyDependedOn() {
		assertEnabled(ModuleDTest.class, "moduleB/ServiceComponentB.java");
	}

	@Test // GH-31
	void disablesForChangesInUnrelatedModule() {
		assertDisabled(ModuleDTest.class, "moduleB/ServiceComponentE.java");
	}

	@Test // GH-31
	void enablesTestInRootModule() {
		assertEnabled(ModulithTest.class);
	}

	@Test // GH-31
	void enablesForClasspathFileChange() {

		var pomXml = new ModifiedFile("pom.xml");

		assertEnabled(ModuleDTest.class, true, Stream.of(pomXml));
	}

	private void assertEnabled(Class<?> type, String... files) {
		assertEnabled(type, true, files);
	}

	private void assertDisabled(Class<?> type, String... files) {
		assertEnabled(type, false, files);
	}

	private void assertEnabled(Class<?> type, boolean expected, String... files) {

		var modifiedFiles = Arrays.stream(files)
				.map("src/main/java/com/acme/myproject/"::concat)
				.map(ModifiedFile::new);

		assertEnabled(type, expected, modifiedFiles);
	}

	private void assertEnabled(Class<?> type, boolean expected, Stream<ModifiedFile> files) {

		assertThat(condition.evaluate(new ConditionContext(type, Changes.of(files, OnNoChange.EXECUTE_ALL))))
				.extracting(ConditionEvaluationResult::isDisabled)
				.isNotEqualTo(expected);
	}
}
