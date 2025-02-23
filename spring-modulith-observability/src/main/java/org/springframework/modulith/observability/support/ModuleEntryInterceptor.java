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
package org.springframework.modulith.observability.support;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private final ObservationContext context;
	private final @Nullable ModulithObservationConvention convention;

	/**
	 * Creates a new {@link ModuleEntryInterceptor} for the given {@link ObservedModule}, {@link ObservationRegistry} and
	 * {@link ModulithObservationConvention}.
	 *
	 * @param module must not be {@literal null}.
	 * @param context must not be {@literal null}.
	 * @param convention can be {@literal null}.
	 */
	private ModuleEntryInterceptor(ObservedModule module, ObservationContext context,
			@Nullable ModulithObservationConvention convention) {

		Assert.notNull(module, "ObservedModule must not be null!");
		Assert.notNull(context, "ObservationContext must not be null!");

		this.module = module;
		this.context = context;
		this.convention = convention;
	}

	public static ModuleEntryInterceptor of(ObservedModule module, ObservationContext observationContext) {
		return of(module, observationContext, null);
	}

	public static ModuleEntryInterceptor of(ObservedModule module, ObservationContext observationContext,
			@Nullable ModulithObservationConvention custom) {

		return CACHE.computeIfAbsent(module.getIdentifier(), __ -> {
			return new ModuleEntryInterceptor(module, observationContext, custom);
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {

		var moduleIdentifier = module.getIdentifier();
		var observationRegistry = context.getObservationRegistry();
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

		var modulithContext = new ModulithContext(module, invocation, context.getEnvironment());
		var observation = Observation.createNotStarted(convention, DEFAULT,
				() -> modulithContext, observationRegistry);

		return new ObservingInvocationAdapter(observation).invoke(invocation);
	}

	private class ObservingInvocationAdapter implements MethodInterceptor {

		private final Observation observation;

		ObservingInvocationAdapter(Observation observation) {
			this.observation = observation;
		}

		/*
		 * (non-Javadoc)
		 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
		 */
		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {

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
}
