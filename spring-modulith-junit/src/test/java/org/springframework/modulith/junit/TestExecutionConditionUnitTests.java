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
package org.springframework.modulith.junit;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.junit.diff.ModifiedFile;

/**
 * Unit tests for {@link TestExecutionCondition}.
 *
 * @author Oliver Drotbohm
 */
class TestExecutionConditionUnitTests {

	@Test // GH-1391
	void fallsBackToEnabledTestIfMultipleMainClassesFound() {

		var changes = Changes.of(Stream.of(new ModifiedFile("Foo.java")), new StandardEnvironment());
		var ctx = new TestExecutionCondition.ConditionContext(getClass(), changes);

		assertThat(new TestExecutionCondition().evaluate(ctx).isDisabled()).isFalse();
	}

	@SpringBootConfiguration
	static class First {}

	@SpringBootConfiguration
	static class Second {}
}
