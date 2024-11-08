package org.springframework.modulith.observability;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;

enum ModulithMetrics implements MeterDocumentation {

	/**
	 * Counter for the events.
	 */
	EVENTS {
		@Override
		public String getName() {
			return "modulith.events.processed";
		}

		@Override
		public Meter.Type getType() {
			return Meter.Type.COUNTER;
		}

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
			@Override
			public String asString() {
				return "event.type";
			}
		},

		/**
		 * Name of the module.
		 */
		MODULE_NAME {
			@Override
			public String asString() {
				return "module.name";
			}
		}
	}
}
