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
package org.springframework.modulith.core.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.ApplicationModuleInitializer;

/**
 * Tests for {@link ApplicationModuleInitializerRuntimeVerification}.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class ApplicationModuleInitializerRuntimeVerificationTests {

	@Test // GH-348
	void startsWithoutApplicationModuleInititalizersPresent() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ApplicationModuleInitializerRuntimeVerification.class))
				.run(ctx -> {
					assertThat(ctx).hasNotFailed();
				});
	}

	@Test // GH-348
	void rejectsPresenceOfAppliactionModuleInitializerBeanIfRuntimeArtifactIsMissing() {

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ApplicationModuleInitializerRuntimeVerification.class))
				.withBean(ApplicationModuleInitializer.class, () -> mock(ApplicationModuleInitializer.class))
				.run(ctx -> {

					assertThat(ctx)
							.hasFailed()
							.getFailure()
							.isInstanceOf(IllegalStateException.class)
							.hasMessageContaining("ApplicationModuleInitializer") // Type
							.hasMessageContaining("applicationModuleInitializer") // Bean name
							.hasMessageContaining("spring-modulith-runtime"); // Missing artifact
				});
	}
}
