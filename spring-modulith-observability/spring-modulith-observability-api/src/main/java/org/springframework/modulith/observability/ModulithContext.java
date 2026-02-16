/*
 * Copyright 2024-2026 the original author or authors.
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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation.Context;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.modulith.observability.ModulithObservations.HighKeys;
import org.springframework.modulith.observability.ModulithObservations.LowKeys;
import org.springframework.util.Assert;

/**
 * A {@link Context} for Modulithic applications.
 *
 * @author Marcin Grzejsczak
 * @author Oliver Drotbohm
 * @since 1.4
 */
public class ModulithContext extends Context {

	public static final String DEFAULT_CONVENTION_NAME = "module.invocations";

	private final ObservedModule module;
	private final MethodInvocation invocation;
	private final @Nullable String applicationName;

	/**
	 * Creates a new {@link ModulithContext} for the given {@link ObservedModule}, {@link MethodInvocation} and
	 * {@link Environment}.
	 *
	 * @param module must not be {@literal null}.
	 * @param invocation must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	public ModulithContext(ObservedModule module, MethodInvocation invocation, Environment environment) {

		Assert.notNull(module, "ObservedModule must not be null!");
		Assert.notNull(invocation, "MethodInvocation must not be null!");
		Assert.notNull(environment, "Environment must not be null!");

		this.module = module;
		this.invocation = invocation;
		this.applicationName = environment.getProperty("spring.application.name");
	}

	public ObservedModule getModule() {
		return module;
	}

	public MethodInvocation getInvocation() {
		return invocation;
	}

	public @Nullable String getApplicationName() {
		return applicationName;
	}

	public KeyValues getLowCardinalityValues() {

		return KeyValues.of(KeyValue.of(LowKeys.MODULE_IDENTIFIER, module.getIdentifier().toString()))
				.and(KeyValue.of(LowKeys.MODULE_NAME, module.getDisplayName()))
				.and(KeyValue.of(LowKeys.INVOCATION_TYPE, getInvocationType()));
	}

	public KeyValues getHighCardinalityValues() {
		return KeyValues.of(HighKeys.MODULE_METHOD.withValue(invocation.getMethod().getName()));
	}

	/*
	 *
	 * (non-Javadoc)
	 * @see io.micrometer.observation.Observation.Context#getContextualName()
	 */
	@Override
	public String getContextualName() {
		return module.getDisplayName() + " > " + module.format(invocation);
	}

	private String getInvocationType() {
		return module.isEventListenerInvocation(invocation) ? "event-listener" : "bean";
	}
}
