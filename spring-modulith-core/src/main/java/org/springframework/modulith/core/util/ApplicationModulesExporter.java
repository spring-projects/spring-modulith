/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.modulith.core.util;

import static java.util.stream.Collectors.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.util.Assert;

/**
 * Export the structure of {@link ApplicationModules} as JSON.
 *
 * @author Oliver Drotbohm
 */
public class ApplicationModulesExporter {

	private static final Function<Set<DependencyType>, Set<DependencyType>> REMOVE_DEFAULT_DEPENDENCY_TYPE_IF_OTHERS_PRESENT = it -> {

		if (it.stream().anyMatch(type -> type != DependencyType.DEFAULT)) {
			it.remove(DependencyType.DEFAULT);
		}

		return it;
	};

	private static final Collector<ApplicationModuleDependency, ?, Set<DependencyType>> MAPPER = mapping(
			ApplicationModuleDependency::getDependencyType,
			collectingAndThen(toSet(), REMOVE_DEFAULT_DEPENDENCY_TYPE_IF_OTHERS_PRESENT));

	private final ApplicationModules modules;

	/**
	 * Creates a new {@link ApplicationModulesExporter} for the given {@link ApplicationModules}.
	 *
	 * @param modules must not be {@literal null}.
	 */
	public ApplicationModulesExporter(ApplicationModules modules) {

		Assert.notNull(modules, "ApplicationModules must not be null!");

		this.modules = modules;
	}

	/**
	 * Simple main method to render the {@link ApplicationModules} instance defined for the Java package given as first
	 * argument.
	 *
	 * @param args a single-element array containing a Java package name to bootstrap an {@link ApplicationModules}
	 *          instance from.
	 */
	public static void main(String[] args) {

		Assert.notNull(args, "Arguments must not be null!");
		Assert.isTrue(args.length == 1, "A java package name is required as only argument!");

		System.out.println(new ApplicationModulesExporter(ApplicationModules.of(args[0])).toJson());
	}

	/**
	 * Returns the {@link ApplicationModules} structure as JSON String.
	 *
	 * @return will never be {@literal null}.
	 */
	public String toJson() {
		return Json.toString(toMap());
	}

	private Map<String, Object> toMap() {

		return modules.stream()
				.collect(
						Collectors.toMap(ApplicationModule::getName, it -> toInfo(it, modules), (l, r) -> r, LinkedHashMap::new));
	}

	private static Map<String, Object> toInfo(ApplicationModule module, ApplicationModules modules) {

		return Map.of( //
				"displayName", module.getDisplayName(), //
				"basePackage", module.getBasePackage().getName(), //
				"dependencies", module.getDependencies(modules).stream() //
						.collect(Collectors.groupingBy(ApplicationModuleDependency::getTargetModule, MAPPER))
						.entrySet() //
						.stream() //
						.map(ApplicationModulesExporter::toInfo) //
						.toList() //
		);
	}

	private static Map<String, Object> toInfo(Entry<ApplicationModule, ? extends Set<DependencyType>> types) {

		return Map.of( //
				"target", types.getKey().getName(), //
				"types", types.getValue() //
		);
	}
}
