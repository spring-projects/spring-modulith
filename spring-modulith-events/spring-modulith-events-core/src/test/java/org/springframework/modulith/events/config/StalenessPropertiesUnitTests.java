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
package org.springframework.modulith.events.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.modulith.events.EventPublication.Status.*;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.NamedExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Unit tests for {@link StalenessProperties}.
 *
 * @author Oliver Drotbohm
 */
class StalenessPropertiesUnitTests {

	@TestFactory // GH-1919
	Stream<DynamicTest> rejectsZeroAndNegativeDurations() {

		var negative = Duration.ofSeconds(-1);

		var tests = Stream.of(
				new $("published", Duration.ZERO),
				new $("published", negative),
				new $("processing", Duration.ZERO),
				new $("processing", negative),
				new $("resubmitted", Duration.ZERO),
				new $("resubmitted", negative),
				new $("resubmission", Duration.ZERO),
				new $("resubmission", negative),
				new $("checkInterval", Duration.ZERO),
				new $("checkInterval", negative),
				new $("checkIntervall", Duration.ZERO),
				new $("checkIntervall", negative));

		return DynamicTest.stream(tests);
	}

	@Test // GH-1919
	void acceptsPositiveAndUnsetDurations() {

		var properties = new StalenessProperties(null, null, Duration.ofMinutes(5), null, null, null);

		assertThat(properties.monitorStaleness()).isTrue();
		assertThat(properties.getStaleness(PROCESSING)).isEqualTo(Duration.ZERO);
	}

	@Test // GH-1919
	void onlyMonitorsStatusesWithConfiguredStaleness() {

		var properties = new StalenessProperties(null, null, Duration.ofMinutes(5), null, null, null);

		assertThat(properties.isMonitored(RESUBMITTED)).isTrue();
		assertThat(properties.isMonitored(PUBLISHED)).isFalse();
		assertThat(properties.isMonitored(PROCESSING)).isFalse();
	}

	@Test // GH-1919
	void doesNotMonitorStatusesThatCannotBecomeStale() {

		var properties = new StalenessProperties(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3),
				null, null, null);

		assertThat(properties.isMonitored(COMPLETED)).isFalse();
		assertThat(properties.isMonitored(FAILED)).isFalse();
	}

	@Test // GH-1919
	void rejectsStalenessLookupForStatusesThatCannotBecomeStale() {

		var properties = new StalenessProperties(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3),
				null, null, null);

		assertThatIllegalArgumentException().isThrownBy(() -> properties.getStaleness(COMPLETED));
		assertThatIllegalArgumentException().isThrownBy(() -> properties.getStaleness(FAILED));
	}

	record $(String property, Duration value, Supplier<StalenessProperties> factory) implements NamedExecutable {

		$(String property, Duration value) {
			this(property, value, switch (property) {
				case "published" -> () -> new StalenessProperties(value, null, null, null, null, null);
				case "processing" -> () -> new StalenessProperties(null, value, null, null, null, null);
				case "resubmitted" -> () -> new StalenessProperties(null, null, value, null, null, null);
				case "resubmission" -> () -> new StalenessProperties(null, null, null, value, null, null);
				case "checkInterval" -> () -> new StalenessProperties(null, null, null, null, value, null);
				case "checkIntervall" -> () -> new StalenessProperties(null, null, null, null, null, value);
				default -> throw new IllegalArgumentException("Unknown property " + property);
			});
		}

		@Override
		public final String toString() {
			return "Rejects %s for %s".formatted(value, property);
		}

		@Override
		public void execute() {

			assertThatIllegalArgumentException()
					.isThrownBy(factory::get)
					.withMessageContaining(property);
		}
	}
}
