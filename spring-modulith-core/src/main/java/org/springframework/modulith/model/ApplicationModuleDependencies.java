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
package org.springframework.modulith.model;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * The materialized, in other words actually present dependencies of the current module towards other modules.
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor(staticName = "of")
public class ApplicationModuleDependencies {

	private final List<ApplicationModuleDependency> dependencies;
	private final ApplicationModules modules;

	/**
	 * Returns whether the dependencies contain the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public boolean contains(ApplicationModule module) {

		Assert.notNull(module, "ApplicationModule must not be null!");

		return dependencies.stream()
				.map(ApplicationModuleDependency::getTargetModule)
				.anyMatch(module::equals);
	}

	/**
	 * Returns whether the dependencies contain the {@link ApplicationModule} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public boolean containsModuleNamed(String name) {

		Assert.hasText(name, "Module name must not be null or empty!");

		return dependencies.stream()
				.map(ApplicationModuleDependency::getTargetModule)
				.map(ApplicationModule::getName)
				.anyMatch(name::equals);
	}

	/**
	 * Returns all {@link DefaultMaterializedDependency} instances as {@link Stream}.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModuleDependency> stream() {
		return dependencies.stream();
	}

	public ApplicationModuleDependencies withType(DependencyType type) {

		var filtered = dependencies.stream()
				.filter(it -> it.getDependencyType().equals(type))
				.toList();

		return ApplicationModuleDependencies.of(filtered, modules);
	}

	/**
	 * Returns whether there are any dependencies at all.
	 *
	 * @return will never be {@literal null}.
	 */
	public boolean isEmpty() {
		return dependencies.isEmpty();
	}
}
