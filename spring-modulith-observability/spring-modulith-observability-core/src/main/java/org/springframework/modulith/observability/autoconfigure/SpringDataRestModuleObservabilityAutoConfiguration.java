/*
 * Copyright 2022-2026 the original author or authors.
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
package org.springframework.modulith.observability.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.rest.webmvc.RepositoryController;
import org.springframework.modulith.observability.ModulithObservationConvention;
import org.springframework.modulith.observability.support.DefaultModulithObservationConvention;
import org.springframework.modulith.observability.support.SpringDataRestModuleObservabilityBeanPostProcessor;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * @author Oliver Drotbohm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RepositoryController.class)
class SpringDataRestModuleObservabilityAutoConfiguration {

	@Bean
	static SpringDataRestModuleObservabilityBeanPostProcessor springDataRestModuleTracingBeanPostProcessor(
			ApplicationModulesRuntime runtime, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ModulithObservationConvention> convention, Environment environment) {

		Supplier<ModulithObservationConvention> defaulted = () -> convention
				.getIfAvailable(() -> DefaultModulithObservationConvention.INSTANCE);

		return new SpringDataRestModuleObservabilityBeanPostProcessor(runtime, () -> observationRegistry.getObject(),
				defaulted, environment);
	}
}
