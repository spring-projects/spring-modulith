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
package org.springframework.modulith.test;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ModuleTypeExcludeFilter}.
 *
 * @author Oliver Drotbohm
 */
class ModuleTypeExcludeFilterUnitTests {

	@Test // GH-725, GH-1216
	void instancesForSameTargetTypeAreEqual() {

		var left = new ModuleTypeExcludeFilter(First.class);
		var right = new ModuleTypeExcludeFilter(First.class);

		assertThat(left).isEqualTo(right);
		assertThat(right).isEqualTo(left);
		assertThat(left).hasSameHashCodeAs(right);
	}

	@Test // GH-1216
	void instancesForDifferentTargetButSameTestSetupTypeAreEqual() {

		var left = new ModuleTypeExcludeFilter(First.class);
		var right = new ModuleTypeExcludeFilter(Second.class);

		assertThat(left).isEqualTo(right);
		assertThat(right).isEqualTo(left);
		assertThat(left).hasSameHashCodeAs(right);
	}

	@Test // GH-1216
	void instancesForDifferentTargetAndDifferentTestSetupTypeAreNotEqual() {

		var left = new ModuleTypeExcludeFilter(Second.class);
		var right = new ModuleTypeExcludeFilter(Third.class);

		assertThat(left).isNotEqualTo(right);
		assertThat(right).isNotEqualTo(left);
		assertThat(left).doesNotHaveSameHashCodeAs(right);
	}

	@ApplicationModuleTest
	static class First {}

	@ApplicationModuleTest
	static class Second {}

	@ApplicationModuleTest(extraIncludes = "foo")
	static class Third {}
}
