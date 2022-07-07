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
package org.springframework.modulith.observability;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.Tracer.SpanInScope;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class ModuleEntryInterceptor implements MethodInterceptor {

	private static Map<String, ModuleEntryInterceptor> CACHE = new HashMap<>();

	private final ObservedModule module;
	private final Tracer tracer;

	public static ModuleEntryInterceptor of(ObservedModule module, Tracer tracer) {

		String name = module.getName();

		return CACHE.computeIfAbsent(name, __ -> {
			return new ModuleEntryInterceptor(module, tracer);
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {

		String moduleName = module.getName();
		Span currentSpan = tracer.currentSpan();

		if (currentSpan != null) {

			BaggageInScope currentBaggage = tracer.getBaggage(ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY);

			if (currentBaggage != null && moduleName.equals(currentBaggage.get())) {
				return invocation.proceed();
			}
		}

		String invokedMethod = module.getInvokedMethod(invocation);

		LOG.trace("Entering {} via {}.", module.getDisplayName(), invokedMethod);

		Span span = tracer.spanBuilder()
				.name(moduleName)
				.tag("module.method", invokedMethod)
				.tag(ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY, moduleName)
				.start();

		try (
				SpanInScope ws = tracer.withSpan(span); //
				BaggageInScope baggage = tracer.createBaggage(ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY, moduleName); //
		) {

			return invocation.proceed();

		} finally {

			LOG.trace("Leaving {}", module.getDisplayName());

			span.end();
		}
	}
}
