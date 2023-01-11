/*
 * Copyright 2022-2023 the original author or authors.
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
import java.time.Month;
import java.time.Year;
import java.util.Objects;

import org.jmolecules.event.types.DomainEvent;
import org.springframework.util.Assert;

/**
 * A {@link DomainEvent} published on the last day of the year.
 *
 * @author Oliver Drotbohm
 */
public class YearHasPassed implements DomainEvent {

	private final Year year;

	/**
	 * Creates a new {@link YearHasPassed} for the given {@link Year}.
	 *
	 * @param year must not be {@literal null}.
	 */
	private YearHasPassed(Year year) {

		Assert.notNull(year, "Year must not be null!");

		this.year = year;
	}

	/**
	 * Creates a new {@link YearHasPassed} for the given {@link Year}.
	 *
	 * @param year must not be {@literal null}.
	 */
	public static YearHasPassed of(Year year) {
		return new YearHasPassed(year);
	}

	/**
	 * Creates a new {@link YearHasPassed} event for the given year.
	 *
	 * @param year a valid year
	 * @return will never be {@literal null}.
	 */
	public static YearHasPassed of(int year) {
		return of(Year.of(year));
	}

	/**
	 * The {@link Year} that has just passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public Year getYear() {
		return year;
	}

	/**
	 * Returns the start date of the year passed.
	 *
	 * @return will never be {@literal null}.
	 */
	LocalDate getStartDate() {
		return LocalDate.of(year.getValue(), Month.JANUARY, 1);
	}

	/**
	 * Returns the end date of the year passed.
	 *
	 * @return will never be {@literal null}.
	 */
	LocalDate getEndDate() {
		return LocalDate.of(year.getValue(), Month.DECEMBER, 31);
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

		if (!(obj instanceof YearHasPassed that)) {
			return false;
		}

		return Objects.equals(year, that.year);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(year);
	}
}
