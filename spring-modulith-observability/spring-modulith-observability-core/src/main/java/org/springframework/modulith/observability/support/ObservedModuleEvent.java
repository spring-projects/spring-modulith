/*
 * Copyright 2026 the original author or authors.
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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Counter.Builder;

import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.observability.ModulithMetrics;

/**
 * An observed event associated with the {@link ApplicationModule} it originates from.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 */
class ObservedModuleEvent {

	private final ApplicationModuleIdentifier identifier;
	private final Object event;

	/**
	 * Creates a new {@link ObservedModuleEvent} for the given {@link ApplicationModuleIdentifier} and event.
	 *
	 * @param identifier must not be {@literal null}.
	 * @param event must not be {@literal null}.
	 */
	ObservedModuleEvent(ApplicationModuleIdentifier identifier, Object event) {

		this.identifier = identifier;
		this.event = event;
	}

	Builder createBuilder() {
		return Counter.builder(getEventCounterName());
	}

	String getEventCounterName() {
		return ModulithMetrics.ALL_EVENTS.getName() + "." + getEventReference();
	}

	String getEventReference() {
		return identifier + "." + event.getClass().getSimpleName();
	}

	boolean hasEventOfType(Class<?> type) {
		return type.isInstance(event);
	}

	Object getEvent() {
		return event;
	}
}
