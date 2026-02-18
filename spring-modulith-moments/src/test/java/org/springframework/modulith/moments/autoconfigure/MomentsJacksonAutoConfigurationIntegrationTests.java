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
package org.springframework.modulith.moments.autoconfigure;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;

import net.javacrumbs.jsonunit.assertj.JsonAssert.ConfigurableJsonAssert;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.util.Locale;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.modulith.moments.HourHasPassed;
import org.springframework.modulith.moments.MonthHasPassed;
import org.springframework.modulith.moments.Quarter;
import org.springframework.modulith.moments.QuarterHasPassed;
import org.springframework.modulith.moments.ShiftedQuarter;
import org.springframework.modulith.moments.WeekHasPassed;
import org.springframework.modulith.moments.YearHasPassed;

/**
 * Integration tests for {@link MomentsJacksonAutoConfiguration}.
 *
 * @author Oliver Drotbohm
 * @soundtrack Jill Scott - Ode to Nikki
 */
@JsonTest
@ImportAutoConfiguration(classes = MomentsJacksonAutoConfiguration.class)
public class MomentsJacksonAutoConfigurationIntegrationTests {

	@Autowired JsonMapper mapper;

	@Test
	void serializesHourHasPassed() {

		var event = HourHasPassed.of(LocalDateTime.now());

		assertDeSerializes(event, it -> {
			it.inPath("$.time").isEqualTo(mapper.writeValueAsString(event.getTime()));
		});
	}

	@Test
	void serializesDayHasPassed() {

		var event = DayHasPassed.of(LocalDate.now());

		assertDeSerializes(event, it -> {
			it.inPath("$.date").isEqualTo(mapper.writeValueAsString(event.getDate()));
		});
	}

	@Test
	void serializesWeekHasPassed() {

		var event = WeekHasPassed.of(Year.of(2026), 5, Locale.ENGLISH);

		assertDeSerializes(event, it -> {
			it.inPath("$.year").isEqualTo("2026");
			it.inPath("$.week").isEqualTo(5);
			it.inPath("$.locale").isEqualTo(Locale.ENGLISH.toString());
		});
	}

	@Test
	void serializesMonthHasPassed() {

		assertDeSerializes(MonthHasPassed.of(YearMonth.of(2026, 2)), it -> {
			it.inPath("$.month").isEqualTo("2026-02");
		});
	}

	@Test
	void serializesQuarterHasPassed() {

		assertDeSerializes(QuarterHasPassed.of(Year.of(2026), Quarter.Q1), it -> {
			it.inPath("$.year").isEqualTo(2026);
			it.inPath("$.quarter.quarter").isEqualTo("Q1");
			it.inPath("$.quarter.startMonth").isEqualTo("1");
		});

		assertDeSerializes(QuarterHasPassed.of(Year.of(2026), ShiftedQuarter.of(Quarter.Q3, Month.FEBRUARY)), it -> {
			it.inPath("$.year").isEqualTo(2026);
			it.inPath("$.quarter.quarter").isEqualTo("Q3");
			it.inPath("$.quarter.startMonth").isEqualTo("2");
		});
	}

	@Test
	void serializesYearHasPassed() {

		assertDeSerializes(YearHasPassed.of(Year.of(2026)), it -> {
			it.inPath("$.year").isEqualTo(2026);
		});
	}

	private void assertDeSerializes(Object object, Consumer<ConfigurableJsonAssert> assertion) {

		var serialized = mapper.writeValueAsString(object);
		assertThat(serialized).isNotEmpty();

		assertion.accept(assertThatJson(serialized));

		var deserialized = mapper.readValue(serialized, object.getClass());
		assertThat(deserialized).isEqualTo(object);
	}

	@SpringBootApplication
	static class SampleApplication {}
}
