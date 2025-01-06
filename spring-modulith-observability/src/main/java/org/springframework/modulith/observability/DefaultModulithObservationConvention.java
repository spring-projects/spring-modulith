/*
 * Copyright 2024-2025 the original author or authors.
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

import io.micrometer.common.KeyValues;

import org.springframework.modulith.observability.ModulithObservations.HighKeys;
import org.springframework.modulith.observability.ModulithObservations.LowKeys;

/**
 * Default implementation of {@link ModulithObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @author Oliver Drotbohm
 * @since 1.4
 */
class DefaultModulithObservationConvention implements ModulithObservationConvention {

	/*
	 * (non-Javadoc)
	 * @see io.micrometer.observation.ObservationConvention#getLowCardinalityKeyValues(io.micrometer.observation.Observation.Context)
	 */
	@Override
	public KeyValues getLowCardinalityKeyValues(ModulithContext context) {

		var keyValues = KeyValues.of(LowKeys.MODULE_KEY.withValue(context.getModule().getIdentifier().toString()));

		return isEventListener(context)
				? keyValues.and(LowKeys.INVOCATION_TYPE.withValue("event-listener"))
				: keyValues;
	}

	/*
	 *
	 * (non-Javadoc)
	 * @see io.micrometer.observation.ObservationConvention#getHighCardinalityKeyValues(io.micrometer.observation.Observation.Context)
	 */
	@Override
	public KeyValues getHighCardinalityKeyValues(ModulithContext context) {

		var method = context.getInvocation().getMethod();

		return KeyValues.of(HighKeys.MODULE_METHOD.withValue(method.getName()));
	}

	/*
	 * (non-Javadoc)
	 * @see io.micrometer.observation.ObservationConvention#getName()
	 */
	@Override
	public String getName() {
		return "module.requests";
	}

	/*
	 * (non-Javadoc)
	 * @see io.micrometer.observation.ObservationConvention#getContextualName(io.micrometer.observation.Observation.Context)
	 */
	@Override
	public String getContextualName(ModulithContext context) {
		return "[" + context.getApplicationName() + "] " + context.getModule().getDisplayName();
	}

	private boolean isEventListener(ModulithContext context) {

		try {
			return context.getModule().isEventListenerInvocation(context.getInvocation());
		} catch (Exception e) {
			return false;
		}
	}
}
