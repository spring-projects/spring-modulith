/*
 * Copyright 2022-2023 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;

import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Integration test for {@link SpringModulithRuntimeAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 * @author Michael Weirauch
 */
class SpringModulithRuntimeAutoConfigurationIntegrationTests {

	@SpringBootApplication
	static class SampleApp {}

	@Test // GH-87
	void bootstrapRegistersRuntimeInstances() {

		new ApplicationContextRunner()
				.withUserConfiguration(SampleApp.class)
				.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class))
				.run(context -> {
					assertThat(context.getBean(ApplicationRuntime.class)).isNotNull();
					assertThat(context.getBean(ApplicationModulesRuntime.class)).isNotNull();
				});
	}

	@Test // GH-160
	void missingArchUnitRuntimeDependencyEscalatesOnContextStartup() {

		new ApplicationContextRunner()
				.withUserConfiguration(SampleApp.class)
				.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class))
				.withClassLoader(new FilteredClassLoader(ClassFileImporter.class))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure().getCause())
							.isInstanceOf(BeanInstantiationException.class)
							.cause().isInstanceOf(MissingRuntimeDependency.class);
				});
	}

	@Test // GH-375
	void registersInitializingListenerIfInitializersPresent() {

		var beanName = "applicationModuleInitializingListener";
		var runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(SpringModulithRuntimeAutoConfiguration.class));

		// No initializer -> no listener
		runner.run(context -> assertThat(context).doesNotHaveBean(beanName));

		// Initializer -> listener
		runner.withBean(ApplicationModuleInitializer.class, () -> () -> {})
				.run(context -> assertThat(context).hasBean(beanName));
	}
}
