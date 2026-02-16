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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.docs.ObservationDocumentation;

public enum ModulithObservations implements ObservationDocumentation {

	/**
	 * Observation related to entering a module.
	 */
	MODULE_ENTRY {

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.observation.docs.ObservationDocumentation#getLowCardinalityKeyNames()
		 */
		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowKeys.values();
		}

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.observation.docs.ObservationDocumentation#getHighCardinalityKeyNames()
		 */
		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return HighKeys.values();
		}

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.observation.docs.ObservationDocumentation#getEvents()
		 */
		@Override
		public Event[] getEvents() {
			return new Event[0];
		}
	};

	public enum LowKeys implements KeyName {

		/**
		 * The identifier of the module.
		 */
		MODULE_IDENTIFIER {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.common.docs.KeyName#asString()
			 */
			@Override
			public String asString() {
				return "module.identifier";
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
		},

		/**
		 * Type of invocation (e.g. event listener).
		 */
		INVOCATION_TYPE {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.common.docs.KeyName#asString()
			 */
			@Override
			public String asString() {
				return "module.invocation-type";
			}
		}
	}

	public enum HighKeys implements KeyName {
		/**
		 * Method executed on a module.
		 */
		MODULE_METHOD {
			@Override
			public String asString() {
				return "module.method";
			}
		}
	}
}
