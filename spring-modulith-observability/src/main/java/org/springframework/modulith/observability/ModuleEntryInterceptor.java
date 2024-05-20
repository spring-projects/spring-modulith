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
package org.springframework.modulith.observability;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;

import java.util.HashMap;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

class ModuleEntryInterceptor implements MethodInterceptor {

	private static Logger LOGGER = LoggerFactory.getLogger(ModuleEntryInterceptor.class);
	private static Map<String, ModuleEntryInterceptor> CACHE = new HashMap<>();
	private static final String MODULE_KEY = ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY;

	private final ObservedModule module;
	private final Tracer tracer;

	/**
	 * Creates a new {@link ModuleEntryInterceptor} for the given {@link ObservedModule} and {@link Tracer}.
	 *
	 * @param module must not be {@literal null}.
	 * @param tracer must not be {@literal null}.
	 */
	private ModuleEntryInterceptor(ObservedModule module, Tracer tracer) {

		Assert.notNull(module, "ObservedModule must not be null!");
		Assert.notNull(tracer, "Tracer must not be null!");

		this.module = module;
		this.tracer = tracer;
	}

	public static ModuleEntryInterceptor of(ObservedModule module, Tracer tracer) {

		return CACHE.computeIfAbsent(module.getName(), __ -> {
			return new ModuleEntryInterceptor(module, tracer);
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {

		var moduleName = module.getName();
		var currentSpan = tracer.currentSpan();
		var currentBaggage = tracer.getBaggage(MODULE_KEY);
		var currentModule = currentBaggage != null ? currentBaggage.get() : null;

		if (currentSpan != null && moduleName.equals(currentModule)) {
			return invocation.proceed();
		}

		var invokedMethod = module.getInvokedMethod(invocation);

		LOGGER.trace("Entering {} via {}.", module.getDisplayName(), invokedMethod);

		var span = tracer.spanBuilder()
				.name(moduleName)
				.tag("module.method", invokedMethod)
				.tag(MODULE_KEY, moduleName)
				.start();

		try (SpanInScope ws = tracer.withSpan(span);
				BaggageInScope baggage = tracer.createBaggageInScope(MODULE_KEY, moduleName)) {

			return invocation.proceed();

		} finally {

			LOGGER.trace("Leaving {}", module.getDisplayName());
			span.end();
		}
	}
}
