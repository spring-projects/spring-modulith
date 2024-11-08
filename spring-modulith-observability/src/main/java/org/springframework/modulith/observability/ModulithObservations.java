package org.springframework.modulith.observability;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

enum ModulithObservations implements ObservationDocumentation {

	/**
	 * Observation related to entering a module.
	 */
	MODULE_ENTRY {
		@Override public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
			return DefaultModulithObservationConvention.class;
		}

		@Override public KeyName[] getLowCardinalityKeyNames() {
			return LowKeys.values();
		}

		@Override public KeyName[] getHighCardinalityKeyNames() {
			return HighKeys.values();
		}

		@Override public Observation.Event[] getEvents() {
			return Events.values();
		}
	};

	enum LowKeys implements KeyName {

		/**
		 * Name of the module.
		 */
		MODULE_KEY {
			@Override public String asString() {
				return "module.key";
			}
		},

		/**
		 * Type of invocation (e.g. event listener).
		 */
		INVOCATION_TYPE {
			@Override public String asString() {
				return "module.invocation-type";
			}
		}
	}

	enum HighKeys implements KeyName {
		/**
		 * Method executed on a module.
		 */
		MODULE_METHOD {
			@Override public String asString() {
				return "module.method";
			}
		}
	}

	enum Events implements Observation.Event {

		/**
		 * Published when an event is sent successfully.
		 */
		EVENT_PUBLICATION_SUCCESS {
			@Override public String getName() {
				return "event.publication.success";
			}

			@Override public String getContextualName() {
				return getName();
			}
		},

		/**
		 * Published when an event is sent with a failure.
		 */
		EVENT_PUBLICATION_FAILURE {
			@Override public String getName() {
				return "event.publication.failure";
			}

			@Override public String getContextualName() {
				return getName();
			}
		}
	}
}
