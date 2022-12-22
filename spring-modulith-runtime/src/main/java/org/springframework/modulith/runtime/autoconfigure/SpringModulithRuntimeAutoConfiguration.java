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
package org.springframework.modulith.runtime.autoconfigure;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.modulith.runtime.ApplicationRuntime;

/**
 * Auto-configuration to register a {@link SpringBootApplicationRuntime} and {@link ApplicationModulesRuntime} as Spring
 * Bean.
 *
 * @author Oliver Drotbohm
 */
@Slf4j
@AutoConfiguration
class SpringModulithRuntimeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ApplicationRuntime.class)
	SpringBootApplicationRuntime modulithsApplicationRuntime(ApplicationContext context) {
		return new SpringBootApplicationRuntime(context);
	}

	@Bean
	@ConditionalOnMissingBean
	ApplicationModulesRuntime modulesRuntime(ApplicationRuntime runtime) {

		var mainClass = runtime.getMainApplicationClass();
		var modules = Executors.newFixedThreadPool(1)
				.submit(() -> SpringModulithRuntimeAutoConfiguration.initializeApplicationModules(mainClass));

		return new ApplicationModulesRuntime(toSupplier(modules), runtime);
	}

	private static ApplicationModules initializeApplicationModules(Class<?> applicationMainClass) {

		LOG.debug("Obtaining Spring Modulith application modules…");

		var result = ApplicationModules.of(applicationMainClass);
		var numberOfModules = result.stream().count();

		if (numberOfModules == 0) {

			LOG.warn("No application modules detected!");

		} else {

			LOG.debug("Detected {} application modules: {}", //
					result.stream().count(), //
					result.stream().map(ApplicationModule::getName).toList());
		}

		return result;
	}

	private static Supplier<ApplicationModules> toSupplier(Future<ApplicationModules> modules) {

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
