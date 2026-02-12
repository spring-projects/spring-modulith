/*
 * Copyright 2018-2026 the original author or authors.
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
package com.acme.myproject.moduleB;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.TestUtils;

import com.acme.myproject.NonVerifyingModuleTest;
import com.acme.myproject.moduleA.ServiceComponentA;
import com.acme.myproject.moduleB.internal.InternalComponentB;

/**
 * @author Oliver Drotbohm
 */
class ModuleBTest {

	@Nested
	static class WithoutMocksTest {

		@Autowired ServiceComponentB serviceComponentB;

		@NonVerifyingModuleTest
		static class Config {}

		@Test
		void failsToStartBecauseServiceComponentAIsMissing() throws Exception {
			TestUtils.assertDependencyMissing(WithoutMocksTest.Config.class, ServiceComponentA.class);
		}
	}

	@Nested
	@NonVerifyingModuleTest
	static class WithMocksTest {

		@Autowired ApplicationContext context;
		@MockitoBean ServiceComponentA serviceComponentA;

		@Test
		void bootstrapsModuleB() {

			context.getBean(ServiceComponentB.class);

			assertThat(Mockito.mockingDetails(context.getBean(ServiceComponentA.class)).isMock()).isTrue();
		}

		@Test
		void considersNestedPackagePartOfTheModuleByDefault() {
			context.getBean(InternalComponentB.class);
		}

		@Test
		void tweaksAutoConfigurationPackageToModulePackage() {

			assertThat(AutoConfigurationPackages.get(context)) //
					.containsExactly(getClass().getPackage().getName());
		}
	}

	@Nested
	@NonVerifyingModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
	static class WithUpstreamModuleTest {

		@Autowired ServiceComponentA componentA;
		@Autowired ServiceComponentB componentB;

		@Test
		void bootstrapsContext() {}
	}
}
