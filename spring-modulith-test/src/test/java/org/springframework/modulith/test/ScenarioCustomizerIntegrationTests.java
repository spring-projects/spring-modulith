/*
 * Copyright 2023-2025 the original author or authors.
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
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ExecutorLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.test.ScenarioCustomizerIntegrationTests.TestScenarioCustomizer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link ScenarioCustomizer}.
 *
 * @author Oliver Drotbohm
 */
@ExtendWith({ SpringExtension.class, ScenarioParameterResolver.class, TestScenarioCustomizer.class })
@ContextConfiguration
class ScenarioCustomizerIntegrationTests {

	@Configuration
	static class TestConfiguration {

		@Bean
		TransactionTemplate transactionTemplate() {
			return mock(TransactionTemplate.class);
		}

		@Bean
		ExecutorService executorService() {
			return Executors.newSingleThreadExecutor();
		}
	}

	@Autowired ExecutorService executorService;

	@BeforeEach
	void setUp() {
		TestScenarioCustomizer.invoked = false;
	}

	@Test // GH-165
	void customizerGetsAppliedForScenarioParameter(Scenario scenario) {

		assertThat(TestScenarioCustomizer.invoked).isTrue();
		assertThat(TestScenarioCustomizer.SAMPLE).isNotNull();

		assertThat(ReflectionTestUtils.getField(scenario, "defaultCustomizer"))
				.isSameAs(TestScenarioCustomizer.SAMPLE);
	}

	@Test // GH-165
	void customizerDoesNotGetAppliedForNoScenarioParameter() {
		assertThat(TestScenarioCustomizer.invoked).isFalse();
	}

	@Test // GH-165
	@SuppressWarnings("unchecked")
	void forwardsExecutorServiceFromApplicationContext(Scenario scenario) {

		var customizer = (Function<ConditionFactory, ConditionFactory>) ReflectionTestUtils.getField(scenario,
				"defaultCustomizer");

		var factory = customizer.apply(Awaitility.await());
		var lifecycle = (ExecutorLifecycle) ReflectionTestUtils.getField(factory, "executorLifecycle");

		assertThat(lifecycle).isNotNull();
		assertThat(lifecycle.supplyExecutorService()).isEqualTo(executorService);
	}

	static class TestScenarioCustomizer implements ScenarioCustomizer {

		static Function<ConditionFactory, ConditionFactory> SAMPLE;
		static boolean invoked = false;

		@Override
		public Function<ConditionFactory, ConditionFactory> getDefaultCustomizer(Method method,
				ApplicationContext context) {

			invoked = true;

			SAMPLE = ScenarioCustomizer.forwardExecutorService(context);

			return SAMPLE;
		}
	}
}
