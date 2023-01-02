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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.Year;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * A quarter that can be shifted to start at a configurable {@link Month}.
 *
 * @author Oliver Drotbohm
 */
@Value(staticConstructor = "of")
public class ShiftedQuarter {

	private static final MonthDay FIRST_DAY = MonthDay.of(Month.JANUARY, 1);
	private static final MonthDay LAST_DAY = MonthDay.of(Month.DECEMBER, 31);

	private final @NonNull Quarter quarter;
	private final @NonNull @Getter(AccessLevel.NONE) Month startMonth;

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

		MonthDay shiftedStart = getStart();
		MonthDay shiftedEnd = getEnd();
		MonthDay reference = MonthDay.from(date);

		Stream<Range> ranges = shiftedEnd.isAfter(shiftedStart)
				? Stream.of(Range.of(shiftedStart, shiftedEnd))
				: Stream.of(Range.of(shiftedStart, LAST_DAY), Range.of(FIRST_DAY, shiftedEnd));

		return ranges.anyMatch(it -> it.contains(reference));
	}

	public MonthDay getStart() {
		return getShifted(quarter.getStart());
	}

	public MonthDay getEnd() {
		return getShifted(quarter.getEnd());
	}

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

	private MonthDay getShifted(MonthDay source) {
		return source.with(source.getMonth().plus(startMonth.getValue() - 1));
	}

	@Value(staticConstructor = "of")
	private static class Range {

		MonthDay start, end;

		public boolean contains(MonthDay day) {

			boolean isAfterStart = start.equals(day) || start.isBefore(day);
			boolean isBeforeEnd = end.equals(day) || end.isAfter(day);

			return isAfterStart && isBeforeEnd;
		}
	}
}
