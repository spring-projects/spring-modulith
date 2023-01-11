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
package org.springframework.modulith.actuator;

import static java.util.stream.Collectors.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.modulith.model.ApplicationModuleDependency;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.model.DependencyType;
import org.springframework.util.Assert;

/**
 * A Spring Boot actuator endpoint to expose the application module structure of a Spring Modulith based application.
 *
 * @author Oliver Drotbohm
 */
@Endpoint(id = "applicationmodules")
public class ApplicationModulesEndpoint {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModulesEndpoint.class);

	private static final Function<Set<DependencyType>, Set<DependencyType>> REMOVE_DEFAULT_DEPENDENCY_TYPE_IF_OTHERS_PRESENT = it -> {

		if (it.stream().anyMatch(type -> type != DependencyType.DEFAULT)) {
			it.remove(DependencyType.DEFAULT);
		}

		return it;
	};

	private static final Collector<ApplicationModuleDependency, ?, Set<DependencyType>> MAPPER = mapping(
			ApplicationModuleDependency::getDependencyType,
			collectingAndThen(toSet(), REMOVE_DEFAULT_DEPENDENCY_TYPE_IF_OTHERS_PRESENT));

	private final Supplier<ApplicationModules> runtime;

	/**
	 * Creates a new {@link ApplicationModulesEndpoint} for the given {@link ModulesRuntime}.
	 *
	 * @param runtime must not be {@literal null}.
	 */
	public ApplicationModulesEndpoint(Supplier<ApplicationModules> runtime) {

		Assert.notNull(runtime, "ModulesRuntime must not be null!");

		LOGGER.debug("Activating Spring Modulith actuator.");

		this.runtime = runtime;
	}

	/**
	 * Returns the {@link ApplicationModules} metadata as {@link Map} (to be rendered as JSON).
	 *
	 * @return will never be {@literal null}.
	 */
	@ReadOperation
	Map<String, Object> getApplicationModules() {

		var modules = runtime.get();

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
						.map(ApplicationModulesEndpoint::toInfo) //
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
