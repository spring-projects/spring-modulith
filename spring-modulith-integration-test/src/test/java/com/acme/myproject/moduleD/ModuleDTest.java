/*
 * Copyright 2023-2026 the original author or authors.
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
package com.acme.myproject.moduleD;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import com.acme.myproject.NonVerifyingModuleTest;
import com.acme.myproject.moduleE.AnotherServiceComponentE;
import com.acme.myproject.moduleE.ServiceComponentE;

/**
 * @author Oliver Drotbohm
 */
@NonVerifyingModuleTest
public class ModuleDTest {

	@Autowired ConfigurableApplicationContext context;

	@Test // GH-320
	void dropsManuallyDeclaredBeanOfNonIncludedModule() {
		assertThat(context.getBeanNamesForType(ServiceComponentE.class)).isEmpty();
	}

	@Test // GH-1489
	void considersBeanFromTestConfiguration() {

		assertThat(context.getBean(AnotherServiceComponentE.class))
				.extracting(MockUtil::isMock)
				.isEqualTo(true);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		AnotherServiceComponentE mock() {
			return Mockito.mock(AnotherServiceComponentE.class);
		}
	}
}
