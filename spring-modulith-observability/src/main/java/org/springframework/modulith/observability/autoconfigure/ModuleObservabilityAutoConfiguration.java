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
package org.springframework.modulith.observability.autoconfigure;

import brave.TracingCustomizer;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationCustomizer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.observability.ApplicationRuntime;
import org.springframework.modulith.observability.ModuleEventListener;
import org.springframework.modulith.observability.ModuleTracingBeanPostProcessor;
import org.springframework.modulith.observability.ModulesRuntime;

/**
 * @author Oliver Drotbohm
 */
@Configuration(proxyBeanMethods = false)
class ModuleObservabilityAutoConfiguration {

	@Bean
	static SpringBootApplicationRuntime modulithsApplicationRuntime(ApplicationContext context) {
		return new SpringBootApplicationRuntime(context);
	}

	@Bean
	static ModuleTracingBeanPostProcessor moduleTracingBeanPostProcessor(ApplicationRuntime runtime,
			Tracer tracer) {
		return new ModuleTracingBeanPostProcessor(runtime, tracer);
	}

	@Bean
	static ModuleEventListener tracingModuleEventListener(ApplicationRuntime runtime, ObjectProvider<Tracer> tracer) {
		return new ModuleEventListener(ModulesRuntime.of(runtime), () -> tracer.getObject());
	}

	/**
	 * Brave-specific auto configuration.
	 *
	 * @author Oliver Drotbohm
	 */
	@ConditionalOnClass(TracingCustomizer.class)
	static class ModulithsBraveIntegrationAutoConfiguration {

		@Bean
		BaggagePropagationCustomizer moduleBaggagePropagationCustomizer() {

			return builder -> builder
					.add(BaggagePropagationConfig.SingleBaggageField
							.local(BaggageField.create(ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY)));
		}

		@Bean
		SpanHandler spanHandler() {

			return new SpanHandler() {

				/*
				 * (non-Javadoc)
				 * @see brave.handler.SpanHandler#end(brave.propagation.TraceContext, brave.handler.MutableSpan, brave.handler.SpanHandler.Cause)
				 */
				@Override
				public boolean end(TraceContext context, MutableSpan span, Cause cause) {

					String value = span.tag(ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY);

					if (value != null) {
						span.localServiceName(value);
						return true;
					}

					BaggageField field = BaggageField.getByName(context, ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY);
					value = field.getValue();

					if (value != null) {
						span.localServiceName(value);
					}

					return true;
				}
			};
		}
	}
}
