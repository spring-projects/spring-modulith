/*
 * Copyright 2022-2024 the original author or authors.
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.modulith.moments.HourHasPassed;
import org.springframework.modulith.moments.MonthHasPassed;
import org.springframework.modulith.moments.QuarterHasPassed;
import org.springframework.modulith.moments.ShiftedQuarter;
import org.springframework.modulith.moments.WeekHasPassed;
import org.springframework.modulith.moments.YearHasPassed;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.Assert;

/**
 * Core component to publish passage-of-time events.
 *
 * @author Oliver Drotbohm
 */
public class Moments {

	private static final MonthDay DEC_31ST = MonthDay.of(Month.DECEMBER, 31);

	private final Clock clock;
	private final ApplicationEventPublisher events;
	private final MomentsProperties properties;

	private Duration shift = Duration.ZERO;

	/**
	 * Creates a new {@link Moments} for the given {@link Clock}, {@link ApplicationEventPublisher} and
	 * {@link MomentsProperties}.
	 *
	 * @param clock must not be {@literal null}.
	 * @param events must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	public Moments(Clock clock, ApplicationEventPublisher events, MomentsProperties properties) {

		Assert.notNull(clock, "Clock must not be null!");
		Assert.notNull(events, "ApplicationEventPublisher must not be null!");
		Assert.notNull(properties, "MomentsProperties must not be null!");

		this.clock = clock;
		this.events = events;
		this.properties = properties;
	}

	/**
	 * Triggers event publication every hour.
	 */
	@Scheduled(cron = "@hourly")
	void everyHour() {

		if (properties.isHourly()) {
			emitEventsFor(now().minusHours(1));
		}
	}

	/**
	 * Triggers event publication every midnight.
	 */
	@Scheduled(cron = "@daily")
	void everyMidnight() {
		emitEventsFor(now().toLocalDate().minusDays(1));
	}

	Moments shiftBy(Duration duration) {

		LocalDateTime before = now();
		LocalDateTime after = before.plus(duration);

		this.shift = shift.plus(duration);

		if (duration.isNegative()) {
			return this;
		}

		LocalDateTime current = before.truncatedTo(ChronoUnit.HOURS);
		boolean hourly = properties.isHourly();

		while (current.isBefore(after.truncatedTo(ChronoUnit.HOURS))) {

			LocalDateTime next = hourly ? current.plusHours(1) : current.plusDays(1);

			if (hourly) {
				emitEventsFor(next);
			}

			if (current.toLocalDate().isBefore(next.toLocalDate())) {
				emitEventsFor(current.toLocalDate());
			}

			current = next;
		}

		return this;
	}

	LocalDateTime now() {

		Instant instant = clock.instant().plus(shift);

		return LocalDateTime.ofInstant(instant, properties.getZoneId());
	}

	private void emitEventsFor(LocalDateTime time) {
		events.publishEvent(HourHasPassed.of(time.truncatedTo(ChronoUnit.HOURS)));
	}

	private void emitEventsFor(LocalDate date) {

		// Day has passed
		events.publishEvent(DayHasPassed.of(date));

		var year = Year.from(date);

		// Week has passed
		var weekFields = WeekFields.of(properties.getLocale());
		var field = weekFields.weekOfWeekBasedYear();
		var currentWeek = date.get(field);
		var tomorrowsWeek = date.plusDays(1).get(field);

		if (tomorrowsWeek != currentWeek) {

			var eventYear = Year.of(date.get(weekFields.weekBasedYear()));

			events.publishEvent(WeekHasPassed.of(eventYear, currentWeek, properties.getLocale()));
		}

		// Month has passed
		if (date.getDayOfMonth() == date.lengthOfMonth()) {
			events.publishEvent(MonthHasPassed.of(YearMonth.from(date)));
		}

		// Quarter has passed
		ShiftedQuarter quarter = properties.getShiftedQuarter(date);

		if (quarter.isLastDay(date)) {
			events.publishEvent(QuarterHasPassed.of(year, quarter));
		}

		// Year has passed
		if (MonthDay.from(date).equals(DEC_31ST)) {
			events.publishEvent(YearHasPassed.of(year));
		}
	}
}
