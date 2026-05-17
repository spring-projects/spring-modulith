/*
 * Copyright 2022-2026 the original author or authors.
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
package org.springframework.modulith.moments.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;

import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.modulith.moments.HourHasPassed;
import org.springframework.modulith.moments.MinuteHasPassed;
import org.springframework.modulith.moments.MonthHasPassed;
import org.springframework.modulith.moments.QuarterHasPassed;
import org.springframework.modulith.moments.SecondHasPassed;
import org.springframework.modulith.moments.ShiftedQuarter;
import org.springframework.modulith.moments.WeekHasPassed;
import org.springframework.modulith.moments.YearHasPassed;
import org.springframework.modulith.moments.support.MomentsProperties.Granularity;

/**
 * Unit tests for {@link Moments}.
 *
 * @author Oliver Drotbohm
 * @author John Cunliffe
 */
class MomentsUnitTests {

	static final TemporalUnitOffset ACCEPTABLE_OFFSET = within(50, ChronoUnit.MILLIS);

	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	Clock clock = Clock.systemUTC();

	Moments hourly = new Moments(clock, events, MomentsProperties.DEFAULTS);
	Moments daily = new Moments(clock, events, MomentsProperties.DEFAULTS.withGranularity(Granularity.DAYS));
	Moments minutely = new Moments(clock, events, MomentsProperties.DEFAULTS.withGranularity(Granularity.MINUTES));
	Moments secondly = new Moments(clock, events, MomentsProperties.DEFAULTS.withGranularity(Granularity.SECONDS));

	@Test
	void emitsHourlyEventOnTimeShift() {

		hourly.shiftBy(Duration.ofDays(1));

		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
		verify(events, times(24)).publishEvent(any(HourHasPassed.class));
	}

	@Test
	void onlyEmitsDailyEventOnTimeShiftIfConfigured() {

		daily.shiftBy(Duration.ofDays(1));

		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
		verify(events, never()).publishEvent(any(HourHasPassed.class));
	}

	@Test
	void emitsMonthHasPassedForShiftAcrossMonths() {

		LocalDate now = LocalDate.now(clock);
		int numberOfDaysIntoNextMonth = now.lengthOfMonth() - now.getDayOfMonth() + 1;
		Duration shift = Duration.ofDays(numberOfDaysIntoNextMonth);

		daily.shiftBy(shift);

		verify(events, times(numberOfDaysIntoNextMonth)).publishEvent(any(DayHasPassed.class));
		verify(events, times(1)).publishEvent(any(MonthHasPassed.class));
	}

	@Test
	void doesNotEmitAnyEventsOnNegativeTimeShift() {

		hourly.shiftBy(Duration.ofDays(-1));

		verifyNoInteractions(events);
	}

	@Test
	void emitsHourHasPassedOnScheduledMethod() {

		hourly.everyHour();

		verify(events, times(1)).publishEvent(any(HourHasPassed.class));
	}

	@Test
	void emitsDayHasPassedOnScheduledMethod() {

		hourly.everyMidnight();

		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
	}

	@Test
	void emitsWeekHasPassedIfWeekIsExceeded() {

		LocalDate now = LocalDate.now(clock);
		int weekOfYear = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());

		daily.shiftBy(Duration.ofDays(7));

		WeekHasPassed reference = WeekHasPassed.of(Year.from(now), weekOfYear, Locale.getDefault());

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test
	void emitsWeekHasPassedWithCustomLocaleIfConfigured() {

		Locale locale = Locale.GERMAN;
		MomentsProperties properties = MomentsProperties.DEFAULTS.withLocale(locale);

		LocalDate now = LocalDate.now(clock);
		int weekOfYear = now.get(WeekFields.of(locale).weekOfWeekBasedYear());

		new Moments(clock, events, properties).shiftBy(Duration.ofDays(7));

		WeekHasPassed reference = WeekHasPassed.of(Year.from(now), weekOfYear, locale);

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test
	void emitsQuarterHasPassed() {

		LocalDate now = LocalDate.now(clock);
		Duration duration = getNumberOfDaysForThreeMonth(now);

		ShiftedQuarter quarter = MomentsProperties.DEFAULTS //
				.withGranularity(Granularity.DAYS) //
				.getShiftedQuarter(now);

		daily.shiftBy(duration);

		QuarterHasPassed reference = QuarterHasPassed.of(Year.from(now), quarter);

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test
	void emitsYearHasPassed() {

		daily.shiftBy(Duration.ofDays(365));

		YearHasPassed reference = YearHasPassed.of(Year.now());

		verify(events, times(1)).publishEvent(eq(reference));
	}

	@Test
	void shiftsTimeForDuration() {

		Duration duration = Duration.ofHours(4);
		LocalDateTime before = hourly.now();
		LocalDateTime after = hourly.shiftBy(duration).now();

		assertThat(before.plus(duration)).isCloseTo(after, within(200, ChronoUnit.MILLIS));
	}

	@Test // GH-359
	void returnsInstant() {

		var instant = hourly.instant();
		var now = hourly.now();

		assertThat(LocalDateTime.ofInstant(instant, ZoneOffset.UTC)).isCloseTo(now, ACCEPTABLE_OFFSET);
	}

	@Test // GH-1490
	void resettingWipesShift() {

		var now = hourly.instant();
		var afterShift = hourly.shiftBy(Duration.ofMinutes(1)).instant();

		assertThat(now.plus(Duration.ofMinutes(1))).isCloseTo(afterShift, ACCEPTABLE_OFFSET);

		var afterReset = hourly.reset().instant();

		assertThat(afterReset).isCloseTo(now, ACCEPTABLE_OFFSET);
	}

	private Duration getNumberOfDaysForThreeMonth(LocalDate date) {

		int days = 0;

		for (int i = 0; i < 3; i++) {
			days += date.lengthOfMonth();
			date = date.plusDays(days);
		}

		return Duration.ofDays(days);
	}

	@Test
	void emitsMinutelyEventOnTimeShift() {

		minutely.shiftBy(Duration.ofMinutes(5));

		verify(events, times(5)).publishEvent(any(MinuteHasPassed.class));
		verify(events, never()).publishEvent(any(SecondHasPassed.class));
	}

	@Test
	void minutelyShiftAcrossHourEmitsHourEvent() {

		minutely.shiftBy(Duration.ofMinutes(61));

		verify(events, times(61)).publishEvent(any(MinuteHasPassed.class));
		verify(events, atLeastOnce()).publishEvent(any(HourHasPassed.class));
	}

	@Test
	void emitsSecondlyEventOnTimeShift() {

		secondly.shiftBy(Duration.ofSeconds(3));

		verify(events, times(3)).publishEvent(any(SecondHasPassed.class));
	}

	@Test
	void secondlyShiftAcrossMinuteEmitsMinuteEvent() {

		secondly.shiftBy(Duration.ofSeconds(61));

		verify(events, times(61)).publishEvent(any(SecondHasPassed.class));
		verify(events, atLeastOnce()).publishEvent(any(MinuteHasPassed.class));
	}

	@Test
	void hourlyDoesNotEmitMinuteOrSecondEvents() {

		hourly.shiftBy(Duration.ofHours(2));

		verify(events, never()).publishEvent(any(MinuteHasPassed.class));
		verify(events, never()).publishEvent(any(SecondHasPassed.class));
	}

	@Test
	void everyMinuteEmitsOnlyWhenGranularityIsMinutesOrFiner() {

		minutely.everyMinute();
		verify(events, times(1)).publishEvent(any(MinuteHasPassed.class));

		reset(events);

		hourly.everyMinute();
		verify(events, never()).publishEvent(any(MinuteHasPassed.class));
	}

	@Test
	void everySecondEmitsOnlyWhenGranularityIsSeconds() {

		secondly.everySecond();
		verify(events, times(1)).publishEvent(any(SecondHasPassed.class));

		reset(events);

		minutely.everySecond();
		verify(events, never()).publishEvent(any(SecondHasPassed.class));
	}

	@Test // GH-1688
	void hourlyShiftEmitsHourHasPassedForThePassedHour() {

		var clock = Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC);
		var moments = new Moments(clock, events, MomentsProperties.DEFAULTS);

		moments.shiftBy(Duration.ofHours(1));

		verify(events).publishEvent(HourHasPassed.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
	}

	@Test // GH-1688
	void shiftEmitsEachEventForThePeriodThatActuallyPassed() {

		var clock = Clock.fixed(Instant.parse("2026-05-17T10:59:59Z"), ZoneOffset.UTC);
		var moments = new Moments(clock, events, MomentsProperties.DEFAULTS.withGranularity(Granularity.SECONDS));

		moments.shiftBy(Duration.ofSeconds(1));

		verify(events).publishEvent(SecondHasPassed.of(LocalDateTime.of(2026, 5, 17, 10, 59, 59)));
		verify(events).publishEvent(MinuteHasPassed.of(LocalDateTime.of(2026, 5, 17, 10, 59)));
		verify(events).publishEvent(HourHasPassed.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
	}
}
