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
package org.springframework.modulith.observability.support;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;

/**
 * @author Marcin Grzejszczak
 * @author Oliver Drotbohm
 * @since 1.4
 */
enum ModulithMetrics implements MeterDocumentation {

	/**
	 * Counter for the events.
	 */
	EVENTS {

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.core.instrument.docs.MeterDocumentation#getName()
		 */
		@Override
		public String getName() {
			return "modulith.events.processed";
		}

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.core.instrument.docs.MeterDocumentation#getType()
		 */
		@Override
		public Meter.Type getType() {
			return Meter.Type.COUNTER;
		}

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.core.instrument.docs.MeterDocumentation#getKeyNames()
		 */
		@Override
		public KeyName[] getKeyNames() {
			return LowKeys.values();
		}
	};

	enum LowKeys implements KeyName {
		/**
		 * Type of the emitted event.
		 */
		EVENT_TYPE {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.common.docs.KeyName#asString()
			 */
			@Override
			public String asString() {
				return "event.type";
			}
		},

		/**
		 * Name of the module.
		 */
		MODULE_NAME {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.common.docs.KeyName#asString()
			 */
			@Override
			public String asString() {
				return "module.name";
			}
		}
	}
}
