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
package org.springframework.modulith.observability.support;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.ContextView;
import io.micrometer.observation.ObservationFilter;

import java.util.Objects;

import org.springframework.modulith.observability.ModulithObservations;

/**
 * Ensures that {@link ModulithObservations.LowKeys#MODULE_IDENTIFIER} gets propagated from parent to child.
 *
 * @author Marcin Grzejszczak
 * @author Oliver Drotbohm
 * @since 1.4
 */
public class ModuleIdentifierPassingObservationFilter implements ObservationFilter {

	/*
	 * (non-Javadoc)
	 * @see io.micrometer.observation.ObservationFilter#map(io.micrometer.observation.Observation.Context)
	 */
	@Override
	public Observation.Context map(Observation.Context context) {

		if (isModuleKeyValueAbsentInCurrent(context) && isModuleKeyValuePresentInParent(context)) {

			var moduleKey = ModulithObservations.LowKeys.MODULE_IDENTIFIER;
			var parent = Objects.requireNonNull(context.getParentObservation());
			var moduleValue = Objects.requireNonNull(parent.getContextView()
					.getLowCardinalityKeyValue(moduleKey.asString()));

			return context.addLowCardinalityKeyValue(moduleKey.withValue(moduleValue.getValue()));
		}

		return context;
	}

	private static boolean isModuleKeyValueAbsentInCurrent(ContextView context) {
		return context.getLowCardinalityKeyValue(ModulithObservations.LowKeys.MODULE_IDENTIFIER.asString()) == null;
	}

	private static boolean isModuleKeyValuePresentInParent(ContextView context) {

		var parentObservation = context.getParentObservation();

		return parentObservation != null
				&& parentObservation.getContextView()
						.getLowCardinalityKeyValue(ModulithObservations.LowKeys.MODULE_IDENTIFIER.asString()) != null;
	}
}
