/*
 * Copyright 2022-2024 the original author or authors.
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
package org.springframework.modulith.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * The materialized, in other words actually present, dependencies of the current module towards other modules.
 *
 * @author Oliver Drotbohm
 */
public class ApplicationModuleDependencies {

	private final List<ApplicationModuleDependency> dependencies;
	private final Collection<ApplicationModule> modules;

	/**
	 * Creates a new {@link ApplicationModuleDependencies} for the given {@link List} of
	 * {@link ApplicationModuleDependency} and {@link ApplicationModules}.
	 *
	 * @param dependencies must not be {@literal null}.
	 */
	private ApplicationModuleDependencies(List<ApplicationModuleDependency> dependencies) {

		Assert.notNull(dependencies, "ApplicationModuleDependency list must not be null!");

		this.dependencies = dependencies;
		this.modules = dependencies.stream()
				.map(ApplicationModuleDependency::getTargetModule)
				.distinct()
				.toList();
	}

	/**
	 * Creates a new {@link ApplicationModuleDependencies} for the given {@link List} of
	 * {@link ApplicationModuleDependency} and {@link ApplicationModules}.
	 *
	 * @param dependencies must not be {@literal null}.
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static ApplicationModuleDependencies of(List<ApplicationModuleDependency> dependencies) {
		return new ApplicationModuleDependencies(dependencies);
	}

	/**
	 * Returns whether the dependencies contain the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public boolean contains(ApplicationModule module) {

		Assert.notNull(module, "ApplicationModule must not be null!");

		return modules.contains(module);
	}

	/**
	 * Returns whether the dependencies contain the {@link ApplicationModule} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public boolean containsModuleNamed(String name) {

		Assert.hasText(name, "Module name must not be null or empty!");

		return modules.stream()
				.map(ApplicationModule::getName)
				.anyMatch(name::equals);
	}

	/**
	 * Returns all {@link ApplicationModuleDependency} instances as {@link Stream}.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModuleDependency> stream() {
		return dependencies.stream();
	}

	/**
	 * Return all {@link ApplicationModuleDependency} instances unique by the value extracted using the given
	 * {@link Function}.
	 *
	 * @param extractor will never be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModuleDependency> uniqueStream(Function<ApplicationModuleDependency, Object> extractor) {

		Assert.notNull(extractor, "Extractor function must not be null!");

		var seenTargets = new HashSet<>();

		return dependencies.stream()
				.filter(it -> seenTargets.add(extractor.apply(it)));
	}

	/**
	 * Returns a new {@link ApplicationModuleDependencies} instance containing only the dependencies of the given
	 * {@link DependencyType}.
	 *
	 * @param type must not be {@literal null}.
	 * @return
	 */
	public ApplicationModuleDependencies withType(DependencyType type) {

		Assert.notNull(type, "DependencyType must not be null!");

		var filtered = dependencies.stream()
				.filter(it -> it.getDependencyType().equals(type))
				.toList();

		return ApplicationModuleDependencies.of(filtered);
	}

	/**
	 * Returns whether there are any dependencies at all.
	 *
	 * @return will never be {@literal null}.
	 */
	public boolean isEmpty() {
		return modules.isEmpty();
	}

	/**
	 * Returns whether the dependencies contain the type with the given fully-qualified name.
	 *
	 * @param type must not be {@literal null} or empty.
	 * @return
	 * @since 1.3
	 */
	public boolean contains(String type) {

		Assert.hasText(type, "Type must not be null or empty!");

		return uniqueModules().anyMatch(it -> it.contains(type));
	}

	/**
	 * Returns all unique {@link ApplicationModule}s involved in the dependencies.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModule> uniqueModules() {
		return modules.stream();
	}

	/**
	 * Returns the {@link ApplicationModule} containing the given type.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public ApplicationModule getModuleByType(String name) {

		Assert.hasText(name, "Name must not be null or empty!");

		return uniqueModules()
				.filter(it -> it.contains(name))
				.findFirst()
				.orElse(null);
	}
}
