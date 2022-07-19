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

import lombok.Value;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

import org.jmolecules.event.types.DomainEvent;

/**
 * A {@link DomainEvent} published on the last day of the year.
 *
 * @author Oliver Drotbohm
 */
@Value(staticConstructor = "of")
public class YearHasPassed implements DomainEvent {

	/**
	 * The month that has just passed.
	 */
	private final Year year;

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
}
