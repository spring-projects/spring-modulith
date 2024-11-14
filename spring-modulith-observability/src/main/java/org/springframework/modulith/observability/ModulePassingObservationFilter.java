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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * Ensures that {@link ModulithObservations.LowKeys#MODULE_KEY} gets propagated from parent
 * to child.
 * 
 * @author Marcin Grzejszczak
 * @since 1.3
 */
public class ModulePassingObservationFilter implements ObservationFilter {

	@Override 
	public Observation.Context map(Observation.Context context) {
		if (isModuleKeyValueAbsentInCurrent(context) && isModuleKeyValuePresentInParent(context)) {
			return context.addLowCardinalityKeyValue(ModulithObservations.LowKeys.MODULE_KEY.withValue(context.getParentObservation().getContextView().getLowCardinalityKeyValue(ModulithObservations.LowKeys.MODULE_KEY.asString()).getValue()));
		}
		return context;
	}

	private static boolean isModuleKeyValueAbsentInCurrent(Observation.ContextView context) {
		return context.getLowCardinalityKeyValue(ModulithObservations.LowKeys.MODULE_KEY.asString()) == null;
	}

	private static boolean isModuleKeyValuePresentInParent(Observation.ContextView context) {
		return context.getParentObservation() != null && context.getParentObservation().getContextView().getLowCardinalityKeyValue(ModulithObservations.LowKeys.MODULE_KEY.asString()) != null;
	}
}