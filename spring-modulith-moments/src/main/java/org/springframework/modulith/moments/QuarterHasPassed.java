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

import lombok.NonNull;
import lombok.Value;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

import org.jmolecules.event.types.DomainEvent;

/**
 * A {@link DomainEvent} published once a quarter has passed.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
@Value(staticConstructor = "of")
public class QuarterHasPassed implements DomainEvent {

	private final @NonNull Year year;
	private final @NonNull ShiftedQuarter quarter;

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
}
