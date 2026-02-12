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
package org.springframework.modulith.observability.support;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.observability.support.ModulithObservations.LowKeys;
import org.springframework.util.Assert;

/**
 * {@link MethodInterceptor} to create {@link Observation}s.
 *
 * @author Marcin Grzejszczak
 * @author Oliver Drotbohm
 */
class ModuleEntryInterceptor implements MethodInterceptor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModuleEntryInterceptor.class);
	private static Map<ApplicationModuleIdentifier, ModuleEntryInterceptor> CACHE = new HashMap<>();

	private static final ModulithObservationConvention DEFAULT = new DefaultModulithObservationConvention();

	private final ObservedModule module;
	private final ObservationRegistry observationRegistry;
	private final @Nullable ModulithObservationConvention customModulithObservationConvention;
	private final Environment environment;

	/**
	 * Creates a new {@link ModuleEntryInterceptor} for the given {@link ObservedModule} and {@link ObservationRegistry}.
	 *
	 * @param module must not be {@literal null}.
	 * @param observationRegistry must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	private ModuleEntryInterceptor(ObservedModule module, ObservationRegistry observationRegistry,
			Environment environment) {
		this(module, observationRegistry, null, environment);
	}

	/**
	 * Creates a new {@link ModuleEntryInterceptor} for the given {@link ObservedModule}, {@link ObservationRegistry} and
	 * {@link ModulithObservationConvention}.
	 *
	 * @param module must not be {@literal null}.
	 * @param observationRegistry must not be {@literal null}.
	 * @param custom must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	private ModuleEntryInterceptor(ObservedModule module, ObservationRegistry observationRegistry,
			ModulithObservationConvention custom, Environment environment) {

		Assert.notNull(module, "ObservedModule must not be null!");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null!");

		this.module = module;
		this.observationRegistry = observationRegistry;
		this.customModulithObservationConvention = custom;
		this.environment = environment;
	}

	public static ModuleEntryInterceptor of(ObservedModule module, ObservationRegistry observationRegistry,
			Environment environment) {
		return of(module, observationRegistry, null, environment);
	}

	public static ModuleEntryInterceptor of(ObservedModule module, ObservationRegistry observationRegistry,
			ModulithObservationConvention custom, Environment environment) {

		return CACHE.computeIfAbsent(module.getIdentifier(), __ -> {
			return new ModuleEntryInterceptor(module, observationRegistry, custom, environment);
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {

		var moduleIdentifier = module.getIdentifier();
		var currentObservation = observationRegistry.getCurrentObservation();
		String currentModule = null;

		if (currentObservation != null) {

			var moduleKey = currentObservation.getContextView().getLowCardinalityKeyValue(LowKeys.MODULE_KEY.asString());
			currentModule = moduleKey != null ? moduleKey.getValue() : null;
		}

		if (currentObservation != null && Objects.equals(moduleIdentifier.toString(), currentModule)) {
			// Same module
			return invocation.proceed();
		}

		var invokedMethod = module.getInvokedMethod(invocation);

		LOGGER.trace("Entering {} via {}.", module.getDisplayName(), invokedMethod);

		var modulithContext = new ModulithContext(module, invocation, environment);
		var observation = Observation.createNotStarted(customModulithObservationConvention, DEFAULT,
				() -> modulithContext, observationRegistry);

		try (Scope scope = observation.start().openScope()) {

			var proceed = invocation.proceed();
			observation.event(ModulithObservations.Events.EVENT_PUBLICATION_SUCCESS);

			return proceed;

		} catch (Exception ex) {

			observation.error(ex);
			observation.event(ModulithObservations.Events.EVENT_PUBLICATION_FAILURE);

			throw ex;

		} finally {

			LOGGER.trace("Leaving {}", module.getDisplayName());
			observation.stop();
		}
	}
}
