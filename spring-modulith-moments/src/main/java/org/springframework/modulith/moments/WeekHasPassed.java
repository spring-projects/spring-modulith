/*
 * Copyright 2022 the original author or authors.
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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.Locale;

import org.jmolecules.event.types.DomainEvent;

/**
 * A {@link DomainEvent} published if a week has passed. The semantics of what constitutes are depended on the
 * {@link Locale} provided.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
@Value(staticConstructor = "of")
public class WeekHasPassed implements DomainEvent {

	/**
	 * The year of the week that has just passed.
	 */
	private final @NonNull Year year;

	/**
	 * The week of the {@link Year} that has just passed.
	 */
	private final int week;

	/**
	 * The {@link Locale} to be used to calculate the start date of the week.
	 */
	private final @NonNull @Getter(AccessLevel.NONE) Locale locale;

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
}
