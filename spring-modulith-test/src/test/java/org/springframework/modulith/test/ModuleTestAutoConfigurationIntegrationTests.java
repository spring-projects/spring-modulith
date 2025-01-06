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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests for {@link ModuleTestAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 */
class ModuleTestAutoConfigurationIntegrationTests {

	@Test // GH-345
	void bootstrapsTestWithManualEntityScanOfModulithPackage() {

		new ApplicationContextRunner()
				.withBean(ModuleTestExecution.class, ModuleTestExecution.of(ModuleTest.class))
				.withConfiguration(AutoConfigurations.of(ModuleTestAutoConfiguration.class))
				.withUserConfiguration(ManualModulithEntityScan.class)
				.run(ctx -> {

					assertThat(ctx).hasNotFailed();
					assertThat(ctx.getBean(EntityScanPackages.class).getPackageNames())
							.containsExactlyInAnyOrder("org.springframework.modulith.test",
									"org.springframework.modulith.events.jpa");
				});
	}

	@SpringBootApplication
	@ApplicationModuleTest
	static class ModuleTest {}

	@EnableAutoConfiguration
	@EntityScan("org.springframework.modulith.events.jpa")
	static class ManualModulithEntityScan {}
}
