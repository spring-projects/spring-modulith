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
package org.springframework.modulith.runtime.autoconfigure;

import static org.assertj.core.api.Assertions.*;

import example.SampleApplication;

import org.junit.jupiter.api.Test;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.modulith.test.TestApplicationModules;

/**
 * Integration tests for {@link ApplicationModulesFileGeneratingProcessor}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModulesFileGeneratingProcessorTests {

	@Test // GH-1457
	void overridesApplicationModulesJson() {

		var generator = new ApplicationContextAotGenerator();
		var generationContext = new TestGenerationContext();

		generator.processAheadOfTime(createContext(), generationContext);

		// Simulate second AOT invocation
		assertThatNoException().isThrownBy(() -> {
			generator.processAheadOfTime(createContext(), generationContext);
		});
	}

	private static GenericApplicationContext createContext() {

		var context = new AnnotationConfigApplicationContext();
		context.register(SampleApplication.class);
		context.register(Config.class);

		return context;
	}

	// No explicit @Configuration to prevent other classpath scanning tests to pick that type up
	static class Config {

		@Bean
		ApplicationRuntime applicationRuntime(ApplicationContext context) {
			return ApplicationRuntime.of(context);
		}

		@Bean
		ApplicationModulesRuntime applicationModulesRuntime(ApplicationRuntime runtime) {
			return new ApplicationModulesRuntime(() -> TestApplicationModules.of(SampleApplication.class), runtime);
		}
	}
}
