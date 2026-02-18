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

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.util.Locale;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.modulith.moments.HourHasPassed;
import org.springframework.modulith.moments.MonthHasPassed;
import org.springframework.modulith.moments.Quarter;
import org.springframework.modulith.moments.QuarterHasPassed;
import org.springframework.modulith.moments.ShiftedQuarter;
import org.springframework.modulith.moments.WeekHasPassed;
import org.springframework.modulith.moments.YearHasPassed;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Autoconfiguration for Jackson to properly (de)serialize Moments events.
 *
 * @author Oliver Drotbohm
 * @since 2.1
 * @soundtrack Jill Scott - Ode to Nikki
 */
@AutoConfiguration
@ConditionalOnClass(JsonMapper.class)
class MomentsJacksonAutoConfiguration {

	@Bean
	MomentsJacksonModule momentsJacksonModule() {
		return new MomentsJacksonModule();
	}

	private static class MomentsJacksonModule extends SimpleModule {

		private static final long serialVersionUID = -824557128527147067L;

		MomentsJacksonModule() {

			setMixInAnnotation(ShiftedQuarter.class, ShiftedQuarterMixin.class);

			setMixInAnnotation(HourHasPassed.class, HourHasPassedMixin.class);
			setMixInAnnotation(DayHasPassed.class, DayHasPassedMixin.class);
			setMixInAnnotation(WeekHasPassed.class, WeekHasPassedMixin.class);
			setMixInAnnotation(MonthHasPassed.class, MonthHasPassedMixin.class);
			setMixInAnnotation(QuarterHasPassed.class, QuarterHasPassedMixin.class);
			setMixInAnnotation(YearHasPassed.class, YearHasPassedMixin.class);
		}

		static abstract class HourHasPassedMixin {

			@JsonCreator
			public static HourHasPassed of(LocalDateTime time) {
				return HourHasPassed.of(time);
			}
		}

		static abstract class DayHasPassedMixin {

			@JsonCreator
			public static DayHasPassed of(LocalDate date) {
				return DayHasPassed.of(date);
			}
		}

		static abstract class WeekHasPassedMixin {

			@JsonCreator
			public static WeekHasPassed of(Year year, int week, Locale locale) {
				return WeekHasPassed.of(year, week, locale);
			}

			@JsonGetter
			abstract Locale getLocale();
		}

		static abstract class MonthHasPassedMixin {

			@JsonCreator
			public static MonthHasPassed of(YearMonth month) {
				return MonthHasPassed.of(month);
			}
		}

		@JsonIgnoreProperties({ "startDate", "endDate" })
		static abstract class QuarterHasPassedMixin {

			@JsonProperty //
			private Year year;

			@JsonProperty //
			private ShiftedQuarter quarter;

			@JsonCreator
			public static QuarterHasPassed of(Year year, ShiftedQuarter quarter) {
				return QuarterHasPassed.of(year, quarter);
			}
		}

		static abstract class YearHasPassedMixin {

			@JsonCreator
			public static YearHasPassed of(Year year) {
				return YearHasPassed.of(year);
			}
		}

		@JsonIgnoreProperties({ "start", "end" })
		static abstract class ShiftedQuarterMixin {

			@JsonProperty //
			private Month startMonth;

			@JsonCreator
			public static ShiftedQuarter of(Quarter quarter, Month startMonth) {
				return ShiftedQuarter.of(quarter, startMonth);
			}
		}
	}
}
