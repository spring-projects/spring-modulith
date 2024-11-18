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
package org.springframework.modulith.observability.autoconfigure;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.task.SimpleAsyncTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.modulith.observability.ModuleEventListener;
import org.springframework.modulith.observability.ModuleObservabilityBeanPostProcessor;
import org.springframework.modulith.observability.ModulePassingObservationFilter;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * @author Oliver Drotbohm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true", matchIfMissing = true)
class ModuleObservabilityAutoConfiguration {

	@Bean
	static ModuleObservabilityBeanPostProcessor moduleTracingBeanPostProcessor(ApplicationModulesRuntime runtime,
			ObjectProvider<ObservationRegistry> observationRegistry, ConfigurableListableBeanFactory factory, Environment environment) {
		return new ModuleObservabilityBeanPostProcessor(runtime, observationRegistry::getObject, factory, environment);
	}

	@Bean
	static ModuleEventListener tracingModuleEventListener(ApplicationModulesRuntime runtime,
			ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<MeterRegistry> meterRegistry) {
		return new ModuleEventListener(runtime, observationRegistry::getObject, meterRegistry::getObject);
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

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(SpanHandler.class)
	static class BraveConfiguration {
		/**
		 * Corresponds to {@link ModulithObservations.LowKeys#MODULE_KEY}
		 */
		private static final String MODULE_KEY_TAG_NAME = "module.key";

		@Bean
		SpanHandler localServiceNameRenamingSpanHandler() {
			return new SpanHandler() {
				@Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
					String tag = span.tag(MODULE_KEY_TAG_NAME);
					if (tag != null) {
						span.localServiceName(tag);
					}
					return super.end(context, span, cause);
				}
			};
		}
	}

}
