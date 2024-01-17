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
package org.springframework.modulith.moments;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Objects;

import org.jmolecules.event.types.DomainEvent;
import org.springframework.util.Assert;

/**
 * A {@link DomainEvent} published once a quarter has passed.
 *
 * @author Oliver Drotbohm
 */
public class QuarterHasPassed implements DomainEvent {

	private final Year year;
	private final ShiftedQuarter quarter;

	/**
	 * Creates a new {@link QuarterHasPassed} for the given {@link Year} and {@link ShiftedQuarter}.
	 *
	 * @param year must not be {@literal null}.
	 * @param quarter must not be {@literal null}.
	 */
	private QuarterHasPassed(Year year, ShiftedQuarter quarter) {

		Assert.notNull(year, "Year must not be null!");
		Assert.notNull(quarter, "ShiftedQuarter must not be null!");

		this.year = year;
		this.quarter = quarter;
	}

	/**
	 * Returns a {@link QuarterHasPassed} for the given {@link Year} and {@link ShiftedQuarter}.
	 *
	 * @param year must not be {@literal null}.
	 * @param quarter must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static QuarterHasPassed of(Year year, ShiftedQuarter quarter) {
		return new QuarterHasPassed(year, quarter);
	}

	/**
	 * Returns a {@link QuarterHasPassed} for the given {@link Year} and logical {@link Quarter}.
	 *
	 * @param year must not be {@literal null}.
	 * @param quarter must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static QuarterHasPassed of(Year year, Quarter quarter) {
		return QuarterHasPassed.of(year, ShiftedQuarter.of(quarter));
	}

	/**
	 * Returns a {@link QuarterHasPassed} for the given {@link Year}, logical {@link Quarter} and start {@link Month}.
	 *
	 * @param year must not be {@literal null}.
	 * @param quarter must not be {@literal null}.
	 * @param startMonth must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static QuarterHasPassed of(Year year, Quarter quarter, Month startMonth) {
		return QuarterHasPassed.of(year, ShiftedQuarter.of(quarter, startMonth));
	}

	/**
	 * Returns the date of the first day of the quarter that has just passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public LocalDate getStartDate() {
		return quarter.getStartDate(year);
	}

	/**
	 * Returns the date of the last day of the quarter that has just passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public LocalDate getEndDate() {
		return quarter.getEndDate(year);
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

		if (!(obj instanceof QuarterHasPassed that)) {
			return false;
		}

		return Objects.equals(quarter, that.quarter) //
				&& Objects.equals(year, that.year);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(quarter, year);
	}
}
