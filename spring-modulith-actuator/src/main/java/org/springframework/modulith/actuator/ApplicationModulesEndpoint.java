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

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.util.ApplicationModulesExporter;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;

/**
 * A Spring Boot actuator endpoint to expose the application module structure of a Spring Modulith based application.
 *
 * @author Oliver Drotbohm
 */
@Endpoint(id = "applicationmodules")
public class ApplicationModulesEndpoint {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModulesEndpoint.class);

	private final SingletonSupplier<String> structure;

	/**
	 * Creates a new {@link ApplicationModulesEndpoint} for the given {@link ApplicationModules}.
	 *
	 * @param runtime must not be {@literal null}.
	 */
	public ApplicationModulesEndpoint(Supplier<ApplicationModules> runtime) {

		Assert.notNull(runtime, "ModulesRuntime must not be null!");

		LOGGER.debug("Activating Spring Modulith actuator.");

		this.structure = SingletonSupplier.of(new ApplicationModulesExporter(runtime.get())::toJson);
	}

	/**
	 * Returns the {@link ApplicationModules} metadata as {@link Map} (to be rendered as JSON).
	 *
	 * @return will never be {@literal null}.
	 */
	@ReadOperation
	String getApplicationModules() {
		return structure.obtain();
	}
}
