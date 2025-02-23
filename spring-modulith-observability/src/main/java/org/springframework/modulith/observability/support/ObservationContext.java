/*
 * Copyright 2025 the original author or authors.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A contextual object to provide access to observability infrastructure.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
public class ObservationContext {

	private final Supplier<ObservationRegistry> observationRegistry;
	private final Supplier<MeterRegistry> meterRegistry;
	private final Environment environment;

	/**
	 * Creates a new {@link ObservationContext} for the given providers of {@link ObservationRegistry} and
	 * {@link MeterRegistry} as well as an {@link Environment}.
	 *
	 * @param observationRegistry must not be {@literal null}.
	 * @param meterRegistry must not be {@literal null}.
	 * @param environment must not be {@literal null}.
	 */
	public ObservationContext(Supplier<ObservationRegistry> observationRegistry, Supplier<MeterRegistry> meterRegistry,
			Environment environment) {

		Assert.notNull(observationRegistry, "ObservationRegistry provider must not be null!");
		Assert.notNull(meterRegistry, "MeterRegistry provider must not be null!");
		Assert.notNull(environment, "Environment must not be null!");

		this.observationRegistry = observationRegistry;
		this.meterRegistry = meterRegistry;
		this.environment = environment;
	}

	/**
	 * Returns the configured {@link ObservationRegistry}.
	 *
	 * @return must not be {@literal null}.
	 */
	ObservationRegistry getObservationRegistry() {
		return observationRegistry.get();
	}

	/**
	 * Returns whether a {@link MeterRegistry} is available.
	 */
	boolean hasMeterRegistry() {
		return getMeterRegistry() != null;
	}

	/**
	 * Returns a {@link MeterRegistry} if available.
	 *
	 * @return can be {@literal null}
	 * @see #hasMeterRegistry()
	 */
	@Nullable
	MeterRegistry getMeterRegistry() {
		return meterRegistry.get();
	}

	/**
	 * Returns the {@link Environment} configured.
	 *
	 * @return will never be {@literal null}.
	 */
	Environment getEnvironment() {
		return environment;
	}
}
