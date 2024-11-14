/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.modulith.observability;

import static org.assertj.core.api.Assertions.*;

import example.ExampleApplication;
import example.sample.SampleComponent;
import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.observability.ModuleObservabilityBeanPostProcessor.ApplicationModuleObservingAdvisor;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;
import org.springframework.modulith.test.TestApplicationModules;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;

/**
 * Integration tests for {@link ModuleObservabilityBeanPostProcessor}.
 *
 * @author Oliver Drotbohm
 */
class ModuleTracingBeanPostProcessorIntegrationTests {

	@Test
	void decoratesExposedComponentsWithTracingInterceptor() {

		SampleComponent bean = SpringApplication
				.run(new Class<?>[] { ExampleApplication.class, ModuleTracingConfiguration.class }, new String[] {})
				.getBean(SampleComponent.class);

		assertThat(bean).isInstanceOfSatisfying(Advised.class, it -> {

			var advisors = it.getAdvisors();

			var asyncIndex = advisorIndex(AsyncAnnotationAdvisor.class, advisors);
			var tracingIndex = advisorIndex(ApplicationModuleObservingAdvisor.class, advisors);

			assertThat(tracingIndex).isGreaterThan(asyncIndex);
		});
	}

	@Configuration
	static class ModuleTracingConfiguration {

		@Bean ModuleObservabilityBeanPostProcessor foo(ConfigurableApplicationContext context) {

			var runtime = ApplicationRuntime.of(context);
			var modulesRuntime = new ApplicationModulesRuntime(() -> TestApplicationModules.of(ExampleApplication.class),
					runtime);

			return new ModuleObservabilityBeanPostProcessor(modulesRuntime, () -> ObservationRegistry.NOOP, context.getBeanFactory(), context.getEnvironment());
		}
	}

	private static int advisorIndex(Class<? extends Advisor> type, Advisor[] advisors) {

		for (int i = 0; i < advisors.length; i++) {
			if (type.isInstance(advisors[i])) {
				return i;
			}
		}

		throw new AssertionError("No advisor of type %s found!".formatted(type.getName()));
	}
}
