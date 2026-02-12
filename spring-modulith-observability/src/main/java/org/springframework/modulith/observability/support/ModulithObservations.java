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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

enum ModulithObservations implements ObservationDocumentation {

	/**
	 * Observation related to entering a module.
	 */
	MODULE_ENTRY {

		/*
		 * (non-Javadoc)
		 * @see io.micrometer.observation.docs.ObservationDocumentation#getDefaultConvention()
		 */
		@Override
		public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
			return DefaultModulithObservationConvention.class;
		}

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
		public Observation.Event[] getEvents() {
			return Events.values();
		}
	};

	enum LowKeys implements KeyName {

		/**
		 * Name of the module.
		 */
		MODULE_KEY {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.common.docs.KeyName#asString()
			 */
			@Override
			public String asString() {
				return "module.key";
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

	enum HighKeys implements KeyName {
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

	enum Events implements Event {

		/**
		 * Published when an event is sent successfully.
		 */
		EVENT_PUBLICATION_SUCCESS {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.observation.Observation.Event#getName()
			 */
			@Override
			public String getName() {
				return "event.publication.success";
			}

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.observation.Observation.Event#getContextualName()
			 */
			@Override
			public String getContextualName() {
				return "event.publication.success";
			}
		},

		/**
		 * Published when an event is sent with a failure.
		 */
		EVENT_PUBLICATION_FAILURE {

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.observation.Observation.Event#getName()
			 */
			@Override
			public String getName() {
				return "event.publication.failure";
			}

			/*
			 * (non-Javadoc)
			 * @see io.micrometer.observation.Observation.Event#getContextualName()
			 */
			@Override
			public String getContextualName() {
				return "event.publication.failure";
			}
		}
	}
}
