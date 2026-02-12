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
package org.springframework.modulith.moments;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;

import org.jmolecules.event.types.DomainEvent;
import org.springframework.util.Assert;

/**
 * A {@link DomainEvent} published if a week has passed. The semantics of what constitutes are depended on the
 * {@link Locale} provided.
 *
 * @author Oliver Drotbohm
 */
public class WeekHasPassed implements DomainEvent {

	private final Year year;
	private final int week;
	private final Locale locale;

	/**
	 * Creates a new {@link WeekHasPassed} for the given {@link Year}, week and {@link Locale}.
	 *
	 * @param year must not be {@literal null}.
	 * @param week must be between 0 and 53 (inclusive).
	 * @param locale must not be {@literal null}.
	 */
	WeekHasPassed(Year year, int week, Locale locale) {

		Assert.notNull(year, "Year must not be null!");
		Assert.isTrue(week >= 0 && week <= 53, "Week must be between 0 and 53!");
		Assert.notNull(locale, "Locale must not be null!");

		this.year = year;
		this.week = week;
		this.locale = locale;
	}

	/**
	 * Creates a new {@link WeekHasPassed} for the given {@link Year}, week and {@link Locale}.
	 *
	 * @param year must not be {@literal null}.
	 * @param week must be between 0 and 53 (inclusive).
	 * @param locale must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static WeekHasPassed of(Year year, int week, Locale locale) {
		return new WeekHasPassed(year, week, locale);
	}

	/**
	 * Creates a new {@link WeekHasPassed} for the given {@link Year} and week of the year.
	 *
	 * @param year must not be {@literal null}.
	 * @param week must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static WeekHasPassed of(Year year, int week) {
		return WeekHasPassed.of(year, week, Locale.getDefault());
	}

	/**
	 * The year of the week that has just passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public Year getYear() {
		return year;
	}

	/**
	 * The {@link Locale} to be used to calculate the start date of the week.
	 *
	 * @return will never be {@literal null}.
	 */
	public int getWeek() {
		return week;
	}

	/**
	 * Returns the start date of the week that has passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public LocalDate getStartDate() {

		return LocalDate.of(year.getValue(), 1, 1)
				.with(WeekFields.of(locale).weekOfYear(), week)
				.with(ChronoField.DAY_OF_WEEK, 1);
	}

	/**
	 * Returns the end date of the week that has passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public LocalDate getEndDate() {
		return getStartDate().plusDays(6);
	}

	/**
	 * @return will never be {@literal null}.
	 */
	Locale getLocale() {
		return locale;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof WeekHasPassed that)) {
			return false;
		}

		return week == that.week //
				&& Objects.equals(year, that.year) //
				&& Objects.equals(locale, that.locale);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(locale, week, year);
	}
}
