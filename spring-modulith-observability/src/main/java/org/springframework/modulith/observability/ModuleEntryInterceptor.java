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

import java.util.HashMap;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.modulith.observability.ModulithObservations.LowKeys;
import org.springframework.util.Assert;

class ModuleEntryInterceptor implements MethodInterceptor {

	private static Logger LOGGER = LoggerFactory.getLogger(ModuleEntryInterceptor.class);
	private static Map<String, ModuleEntryInterceptor> CACHE = new HashMap<>();
	private static final String MODULE_KEY = ModuleTracingBeanPostProcessor.MODULE_BAGGAGE_KEY;

	private static final ModulithObservationConvention DEFAULT = new DefaultModulithObservationConvention();

	private final ObservedModule module;
	private final ObservationRegistry observationRegistry;
	@Nullable private final ModulithObservationConvention customModulithObservationConvention;

	/**
	 * Creates a new {@link ModuleEntryInterceptor} for the given {@link ObservedModule} and {@link ObservationRegistry}.
	 *
	 * @param module              must not be {@literal null}.
	 * @param observationRegistry must not be {@literal null}.
	 */
	private ModuleEntryInterceptor(ObservedModule module, ObservationRegistry observationRegistry) {
		this(module, observationRegistry, null);
	}

	/**
	 * Creates a new {@link ModuleEntryInterceptor} for the given {@link ObservedModule}, {@link ObservationRegistry}
	 * and {@link ModulithObservationConvention}.
	 *
	 * @param module              must not be {@literal null}.
	 * @param observationRegistry must not be {@literal null}.
	 * @param custom              must not be {@literal null}.
	 */
	private ModuleEntryInterceptor(ObservedModule module, ObservationRegistry observationRegistry,
			ModulithObservationConvention custom) {

		Assert.notNull(module, "ObservedModule must not be null!");
		Assert.notNull(observationRegistry, "Tracer must not be null!");

		this.module = module; this.observationRegistry = observationRegistry;
		this.customModulithObservationConvention = custom;
	}

	public static ModuleEntryInterceptor of(ObservedModule module, ObservationRegistry observationRegistry) {
		return of(module, observationRegistry, null);
	}

	public static ModuleEntryInterceptor of(ObservedModule module, ObservationRegistry observationRegistry,
			ModulithObservationConvention custom) {

		return CACHE.computeIfAbsent(module.getName(), __ -> {
			return new ModuleEntryInterceptor(module, observationRegistry, custom);
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override public Object invoke(MethodInvocation invocation) throws Throwable {

		var moduleName = module.getName();
		var currentObservation = observationRegistry.getCurrentObservation();
		String currentModule = null;

		if (currentObservation != null) {
			KeyValue moduleKey = currentObservation.getContextView().getLowCardinalityKeyValue(MODULE_KEY);
			currentModule = moduleKey != null ? moduleKey.getValue() : null;
		}

		if (currentObservation != null && moduleName.equals(currentModule)) {
			// Same module
			return invocation.proceed();
		}

		var invokedMethod = module.getInvokedMethod(invocation);

		LOGGER.trace("Entering {} via {}.", module.getDisplayName(), invokedMethod);

		boolean isEventListener = module.isEventListenerInvocation(invocation);

		// TODO: Good name for metrics
		ModulithContext modulithContext = new ModulithContext(module, invocation);
		var observation = Observation.createNotStarted(customModulithObservationConvention, DEFAULT,
				() -> modulithContext, observationRegistry); if (isEventListener) {
			observation.lowCardinalityKeyValue(LowKeys.INVOCATION_TYPE.withValue("event-listener"));
		} try (Observation.Scope scope = observation.openScope()) {
			Object proceed = invocation.proceed(); observation.event(ModulithObservations.Events.EVENT_PUBLICATION_SUCCESS);
			return proceed;
		} catch (Exception ex) {
			observation.error(ex); observation.event(ModulithObservations.Events.EVENT_PUBLICATION_FAILURE); throw ex;
		} finally {
			LOGGER.trace("Leaving {}", module.getDisplayName()); observation.stop();
		}
	}
}