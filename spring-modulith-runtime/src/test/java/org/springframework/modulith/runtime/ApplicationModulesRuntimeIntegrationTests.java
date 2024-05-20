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
package org.springframework.modulith.runtime;

import static org.assertj.core.api.Assertions.*;

import different.moduleB.ModuleBType;
import example.SampleApplication;
import example.moduleA.ModuleAType;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.runtime.autoconfigure.TestSpringBootApplicationRuntime;
import org.springframework.modulith.test.TestApplicationModules;

/**
 * Integration tests for {@link ApplicationModulesRuntime}.
 *
 * @author Oliver Drotbohm
 */
public class ApplicationModulesRuntimeIntegrationTests {

	ApplicationModules modules = TestApplicationModules.of(SampleApplication.class);

	@Test // GH-611
	void detectsTypeInAdditionalPackageAsApplicationType() {

		var context = SpringApplication.run(SampleApplication.class);
		var applicationRuntime = new TestSpringBootApplicationRuntime(context);

		var runtime = new ApplicationModulesRuntime(() -> modules, applicationRuntime);

		Stream.of(ModuleAType.class, ModuleBType.class)
				.forEach(it -> assertThat(runtime.isApplicationClass(it)).isTrue());
	}

	@Test // GH-587
	void onlyLooksUpApplicationModulesOnce() {

		var context = SpringApplication.run(SampleApplication.class);
		var applicationRuntime = new TestSpringBootApplicationRuntime(context);
		var supplier = new CountingSupplier<>(() -> modules);

		var runtime = new ApplicationModulesRuntime(supplier, applicationRuntime);

		runtime.get();
		runtime.get();

		assertThat(supplier.counter).isEqualTo(1);
	}

	static class CountingSupplier<T> implements Supplier<T> {

		private final Supplier<T> delegate;
		private int counter = 0;

		CountingSupplier(Supplier<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public T get() {

			counter++;

			return delegate.get();
		}
	}
}
