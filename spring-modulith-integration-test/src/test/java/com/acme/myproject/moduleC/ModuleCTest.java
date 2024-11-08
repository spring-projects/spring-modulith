/*
 * Copyright 2018-2024 the original author or authors.
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
package com.acme.myproject.moduleC;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.TestUtils;

import com.acme.myproject.NonVerifyingModuleTest;
import com.acme.myproject.moduleA.ServiceComponentA;
import com.acme.myproject.moduleB.ServiceComponentB;

/**
 * @author Oliver Drotbohm
 */
class ModuleCTest {

	@Nested
	public static class FailsStandaloneTest {

		@NonVerifyingModuleTest
		static class Config {}

		@Test
		void failsStandalone() {
			TestUtils.assertDependencyMissing(FailsStandaloneTest.Config.class, ServiceComponentB.class);
		}
	}

	@Nested
	static class FailsWithDirectDependencyTest {

		@NonVerifyingModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
		static class Config {}

		@Test
		void failsWithDirectDependency() {
			TestUtils.assertDependencyMissing(FailsWithDirectDependencyTest.Config.class, ServiceComponentA.class);
		}
	}

	@Nested
	@NonVerifyingModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
	static class SucceedsWithDirectDependencyPlusItsDependenciesMocksTest {

		@MockitoBean ServiceComponentA serviceComponentA;

		@Test
		void bootstrapsContext() {
			assertThat(serviceComponentA).isNotNull();
		}
	}

	@Nested
	@NonVerifyingModuleTest(BootstrapMode.ALL_DEPENDENCIES)
	static class SucceedsWithAllDependenciesTest {

		@Autowired ServiceComponentA serviceComponentA;
		@Autowired ServiceComponentB serviceComponentB;

		@Test
		void bootstrapsContext() {
			assertThat(serviceComponentA).isNotNull();
			assertThat(serviceComponentB).isNotNull();
		}
	}
}
