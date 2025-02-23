/*
 * Copyright 2022-2025 the original author or authors.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.task.SimpleAsyncTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.modulith.observability.ModulithEventMetricsCustomizer;
import org.springframework.modulith.observability.support.CrossModuleEventCounterFactory;
import org.springframework.modulith.observability.support.LocalServiceRenamingSpanFilter;
import org.springframework.modulith.observability.support.ModuleEventListener;
import org.springframework.modulith.observability.support.ModuleObservabilityBeanPostProcessor;
import org.springframework.modulith.observability.support.ModulePassingObservationFilter;
import org.springframework.modulith.observability.support.ObservationContext;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * @author Oliver Drotbohm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true", matchIfMissing = true)
class ModuleObservabilityAutoConfiguration {

	@Bean
	ObservationContext modulithObservationContext(ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<MeterRegistry> meterRegistry, Environment environment) {
		return new ObservationContext(observationRegistry::getObject, meterRegistry::getIfAvailable, environment);
	}

	@Bean
	static ModuleObservabilityBeanPostProcessor moduleTracingBeanPostProcessor(ApplicationModulesRuntime runtime,
			ObservationContext context, ConfigurableListableBeanFactory factory) {
		return new ModuleObservabilityBeanPostProcessor(runtime, context, factory);
	}

	@Bean
	static ModuleEventListener tracingModuleEventListener(ApplicationModulesRuntime runtime, ObservationContext context,
			CrossModuleEventCounterFactory configurer) {
		return new ModuleEventListener(runtime, context, configurer);
	}

	// TODO: Have a custom thread pool for modulith
	@Bean
	@ConditionalOnThreading(Threading.VIRTUAL)
	SimpleAsyncTaskExecutorCustomizer simpleAsyncTaskExecutorCustomizer() {
		return executor -> executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
	}

	@Bean
	@ConditionalOnThreading(Threading.PLATFORM)
	ThreadPoolTaskExecutorCustomizer threadPoolTaskExecutorCustomizer() {
		return executor -> executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
	}

	@Bean
	ObservationFilter modulePassingObservationFilter() {
		return new ModulePassingObservationFilter();
	}

	@Bean
	LocalServiceRenamingSpanFilter localServiceRenamingSpanFilter() {
		return new LocalServiceRenamingSpanFilter();
	}

	@Bean
	CrossModuleEventCounterFactory modulithEventCounterFactory(
			ObjectProvider<ModulithEventMetricsCustomizer> customizer) {

		var factory = new CrossModuleEventCounterFactory();

		customizer.stream().forEach(it -> it.customize(factory));

		return factory;
	}
}
