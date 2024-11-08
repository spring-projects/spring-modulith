/*
 * Copyright 2019-2024 the original author or authors.
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
package com.acme.myproject.fieldinjected;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.test.ModuleTestExecution;

import com.acme.myproject.NonVerifyingModuleTest;
import com.acme.myproject.moduleA.ServiceComponentA;

/**
 * Integration tests to verify field injection is rejected.
 *
 * @author Oliver Drotbohm
 */
@NonVerifyingModuleTest
class FieldInjectedIntegrationTest {

	@Autowired ModuleTestExecution execution;

	@MockitoBean ServiceComponentA dependency;

	@Test
	void rejectsFieldInjection() {

		ApplicationModules modules = execution.getModules();

		assertThat(execution.getModule().detectDependencies(modules)) //
				.hasMessageContaining("field injection") //
				.hasMessageContaining("WithFieldInjection.a"); // offending field
	}
}
