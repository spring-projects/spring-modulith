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
import java.time.MonthDay;
import java.time.Year;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * A quarter that can be shifted to start at a configurable {@link Month}.
 *
 * @author Oliver Drotbohm
 */
public class ShiftedQuarter {

	private static final MonthDay FIRST_DAY = MonthDay.of(Month.JANUARY, 1);
	private static final MonthDay LAST_DAY = MonthDay.of(Month.DECEMBER, 31);

	private final Quarter quarter;
	private final Month startMonth;

	/**
	 * Creates a new {@link ShiftedQuarter} for the given {@link Quarter} and start {@link Month}.
	 *
	 * @param quarter must not be {@literal null}.
	 * @param startMonth must not be {@literal null}.
	 */
	private ShiftedQuarter(Quarter quarter, Month startMonth) {

		Assert.notNull(quarter, "Quarter must not be null!");
		Assert.notNull(startMonth, "Start Month must not be null!");

		this.quarter = quarter;
		this.startMonth = startMonth;
	}

	/**
	 * Creates a new {@link ShiftedQuarter} for the given {@link Quarter} and start {@link Month}.
	 *
	 * @param quarter must not be {@literal null}.
	 * @param startMonth must not be {@literal null}.
	 */
	public static ShiftedQuarter of(Quarter quarter, Month startMonth) {
		return new ShiftedQuarter(quarter, startMonth);
	}

	/*+
	 * Creates a new ShiftedQuarter for the given logical {@link Quarter}.
	 *
	 * @param quarter must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ShiftedQuarter of(Quarter quarter) {
		return new ShiftedQuarter(quarter, Month.JANUARY);
	}

	/**
	 * Returns the logical {@link Quarter}.
	 *
	 * @return will never be {@literal null}.
	 */
	public Quarter getQuarter() {
		return quarter;
	}

	/**
	 * Returns the next {@link ShiftedQuarter}.
	 *
	 * @return will never be {@literal null}.
	 */
	public ShiftedQuarter next() {
		return new ShiftedQuarter(quarter.next(), startMonth);
	}

	/**
	 * Returns whether the given {@link LocalDate} is contained in the current {@link ShiftedQuarter}.
	 *
	 * @param date must not be {@literal null}.
	 * @return
	 */
	public boolean contains(LocalDate date) {

		Assert.notNull(date, "Reference date must not be null!");

		var shiftedStart = getStart();
		var shiftedEnd = getEnd();
		var reference = MonthDay.from(date);

		var ranges = shiftedEnd.isAfter(shiftedStart)
				? Stream.of(new Range(shiftedStart, shiftedEnd))
				: Stream.of(new Range(shiftedStart, LAST_DAY), new Range(FIRST_DAY, shiftedEnd));

		return ranges.anyMatch(it -> it.contains(reference));
	}

	/**
	 * @return will never be {@literal null}.
	 */
	public MonthDay getStart() {
		return getShifted(quarter.getStart());
	}

	/**
	 * @return will never be {@literal null}.
	 */
	public MonthDay getEnd() {
		return getShifted(quarter.getEnd());
	}

	/**
	 * Returns whether the given {@link LocalDate} is the last day of the {@link ShiftedQuarter}.
	 *
	 * @param date must not be {@literal null}.
	 */
	public boolean isLastDay(LocalDate date) {
		return MonthDay.from(date).equals(getEnd());
	}

	/**
	 * Returns the start date of the {@link ShiftedQuarter} for the given {@link Year}.
	 *
	 * @param year must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public LocalDate getStartDate(Year year) {

		Assert.notNull(year, "Year must not be null!");

		return quarter.getStart()
				.atYear(year.getValue())
				.plusMonths(startMonth.getValue() - 1);
	}

	/**
	 * Returns the end date of the {@link ShiftedQuarter} for the given {@link Year}.
	 *
	 * @param year must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public LocalDate getEndDate(Year year) {

		Assert.notNull(year, "Year must not be null!");

		return getStartDate(year).plusMonths(3).minusDays(1);
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

		if (!(obj instanceof ShiftedQuarter that)) {
			return false;
		}

		return quarter == that.quarter //
				&& startMonth == that.startMonth;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(quarter, startMonth);
	}

	private MonthDay getShifted(MonthDay source) {
		return source.with(source.getMonth().plus(startMonth.getValue() - 1));
	}

	private static record Range(MonthDay start, MonthDay end) {

		public boolean contains(MonthDay day) {

			boolean isAfterStart = start.equals(day) || start.isBefore(day);
			boolean isBeforeEnd = end.equals(day) || end.isAfter(day);

			return isAfterStart && isBeforeEnd;
		}
	}
}
