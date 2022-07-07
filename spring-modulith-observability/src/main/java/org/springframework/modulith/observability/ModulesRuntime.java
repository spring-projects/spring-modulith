/*
 * Copyright 2022 the original author or authors.
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

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.springframework.modulith.model.Modules;

/**
 * Bootstrap type to make sure we only bootstrap the initialization of a {@link Modules} instance per application class
 * once.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
public class ModulesRuntime implements Supplier<Modules> {

	private static final Map<String, ModulesRuntime> MODULES = new HashMap<>();

	private final Supplier<Modules> modules;
	private final ApplicationRuntime runtime;

	/*
	 * (non-Javadoc)
	 * @see java.util.function.Supplier#get()
	 */
	@Override
	public Modules get() {
		return modules.get();
	}

	boolean isApplicationClass(Class<?> type) {
		return runtime.isApplicationClass(type);
	}

	public static ModulesRuntime of(ApplicationRuntime runtime) {

		return MODULES.computeIfAbsent(runtime.getId(), it -> {

			Class<?> mainClass = runtime.getMainApplicationClass();
			Future<Modules> modules = Executors.newFixedThreadPool(1).submit(() -> Modules.of(mainClass));

			return new ModulesRuntime(toSupplier(modules), runtime);
		});
	}

	private static Supplier<Modules> toSupplier(Future<Modules> modules) {

		return () -> {
			try {
				return modules.get();
			} catch (Exception o_O) {
				throw new RuntimeException(o_O);
				// TODO: handle exception
			}
		};
	}
}
