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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.modulith.ApplicationModuleInitializer;

/**
 * @author Oliver Drotbohm
 */
class PrecomputedApplicationModuleInitializerInvokerUnitTests {

	@Test
	void ordersInitializersBasedOnPrecomputedMetadata() throws IOException {

		var resource = new ClassPathResource("application-modules.json");
		var invoker = new PrecomputedApplicationModuleInitializerInvoker(resource);

		var second = new SecondApplicationModuleInitializer();
		var first = new FirstApplicationModuleInitializer();

		invoker.invokeInitializers(Stream.of(second, first));

		assertThat(first.invoked).isBefore(second.invoked);
	}

	static class FirstApplicationModuleInitializer implements ApplicationModuleInitializer {

		LocalDateTime invoked;

		@Override
		public void initialize() {
			invoked = LocalDateTime.now();
		}
	}

	static class SecondApplicationModuleInitializer implements ApplicationModuleInitializer {

		LocalDateTime invoked;

		@Override
		public void initialize() {
			invoked = LocalDateTime.now();
		}
	}
}
