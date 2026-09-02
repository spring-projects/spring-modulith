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

import static org.springframework.modulith.moments.support.MomentsProperties.Granularity.*;

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
import java.util.Collection;
import java.util.stream.Stream;

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
import org.springframework.util.Assert;

/**
 * Core component to publish passage-of-time events.
 *
 * @author Oliver Drotbohm
 * @author John Cunliffe
 */
public class Moments implements Now {

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
	 * Returns the methods to invoke for each {@link Granularity}, bound to this instance.
	 *
	 * @return will never be {@literal null}.
	 */
	public Collection<Task> getTasksToBeScheduled() {
		return Task.of(properties.getGranularity(), this);
	}

	/**
	 * Triggers event publication every second.
	 */
	void everySecond() {

		if (properties.isSecondly()) {
			emitSecondEventFor(now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
		}
	}

	/**
	 * Triggers event publication every minute.
	 */
	void everyMinute() {

		if (properties.isMinutely()) {
			emitMinuteEventFor(now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES));
		}
	}

	/**
	 * Triggers event publication every hour.
	 */
	void everyHour() {

		if (properties.isHourly()) {
			emitEventsFor(now().minusHours(1));
		}
	}

	/**
	 * Triggers event publication every midnight.
	 */
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

		boolean secondly = properties.isSecondly();
		boolean minutely = properties.isMinutely();
		boolean hourly = properties.isHourly();

		ChronoUnit step = secondly ? ChronoUnit.SECONDS
				: minutely ? ChronoUnit.MINUTES
						: ChronoUnit.HOURS;

		LocalDateTime current = before.truncatedTo(step);
		LocalDateTime stop = after.truncatedTo(step);

		while (current.isBefore(stop)) {

			LocalDateTime next = current.plus(1, step);

			if (secondly) {
				emitSecondEventFor(next.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
				if (current.getMinute() != next.getMinute()) {
					emitMinuteEventFor(current.truncatedTo(ChronoUnit.MINUTES));
				}
				if (current.getHour() != next.getHour()) {
					emitEventsFor(current.truncatedTo(ChronoUnit.HOURS));
				}
			} else if (minutely) {
				emitMinuteEventFor(next.minusMinutes(1).truncatedTo(ChronoUnit.MINUTES));
				if (current.getHour() != next.getHour()) {
					emitEventsFor(current.truncatedTo(ChronoUnit.HOURS));
				}
			} else if (hourly) {
				emitEventsFor(current);
			}

			if (current.toLocalDate().isBefore(next.toLocalDate())) {
				emitEventsFor(current.toLocalDate());
			}

			current = next;
		}

		return this;
	}

	Moments reset() {

		this.shift = Duration.ZERO;

		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.moments.support.Now#now()
	 */
	@Override
	public LocalDateTime now() {
		return LocalDateTime.ofInstant(instant(), properties.getZoneId());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.moments.support.Now#today()
	 */
	@Override
	public LocalDate today() {
		return now().toLocalDate();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.moments.support.Now#instant()
	 */
	@Override
	public Instant instant() {
		return clock.instant().plus(shift);
	}

	private void emitEventsFor(LocalDateTime time) {
		events.publishEvent(HourHasPassed.of(time.truncatedTo(ChronoUnit.HOURS)));
	}

	private void emitMinuteEventFor(LocalDateTime time) {
		events.publishEvent(MinuteHasPassed.of(time.truncatedTo(ChronoUnit.MINUTES)));
	}

	private void emitSecondEventFor(LocalDateTime time) {
		events.publishEvent(SecondHasPassed.of(time.truncatedTo(ChronoUnit.SECONDS)));
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

	/**
	 * A method invocation to be scheduled per {@link Granularity}.
	 *
	 * @author Oliver Drotbohm
	 * @since 2.2
	 */
	public static final class Task {

		private final Granularity granularity;
		private final Runnable task;

		/**
		 * Creates a new {@link Task} for the given {@link Granularity} and task.
		 *
		 * @param granularity must not be {@literal null}.
		 * @param task must not be {@literal null}.
		 */
		private Task(Granularity granularity, Runnable task) {

			Assert.notNull(granularity, "Granularity must not be null!");
			Assert.notNull(task, "Runnable must not be null!");

			this.granularity = granularity;
			this.task = task;
		}

		/**
		 * Returns all tasks to be scheduled for the given {@link Granularity} and {@link Moments} instance.
		 *
		 * @param granularity must not be {@literal null}.
		 * @param moments must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		static Collection<Task> of(Granularity granularity, Moments moments) {

			return Stream.of(
					new Task(SECONDS, moments::everySecond),
					new Task(MINUTES, moments::everyMinute),
					new Task(HOURS, moments::everyHour),
					new Task(DAYS, moments::everyMidnight))
					.filter(it -> it.shouldBeScheduledFor(granularity))
					.toList();
		}

		/**
		 * Returns the actual task to be executed.
		 *
		 * @return will never be {@literal null}.
		 */
		public Runnable getTask() {
			return task;
		}

		/**
		 * Return the cron expression to schedule the task at.
		 *
		 * @return will never be {@literal null}.
		 */
		public String getExpression() {
			return granularity.getCron();
		}

		/**
		 * Returns whether the current {@link Task} is associated with the given {@link Granularity}.
		 *
		 * @param granularity must not be {@literal null}.
		 */
		boolean hasGranularity(Granularity granularity) {
			return this.granularity == granularity;
		}

		private boolean shouldBeScheduledFor(Granularity granularity) {
			return granularity.isAtLeastAsFineAs(this.granularity);
		}
	}
}
