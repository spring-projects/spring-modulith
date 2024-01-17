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

import static java.time.MonthDay.*;

import java.time.Month;
import java.time.MonthDay;

import org.springframework.util.Assert;

/**
 * A logical {@link Quarter} of the year.
 *
 * @author Oliver Drotbohm
 */
public enum Quarter {

	Q1(of(Month.JANUARY, 1), of(Month.MARCH, 31)), //
	Q2(of(Month.APRIL, 1), of(Month.JUNE, 30)), //
	Q3(of(Month.JULY, 1), of(Month.SEPTEMBER, 30)), //
	Q4(of(Month.OCTOBER, 1), of(Month.DECEMBER, 31));

	private final MonthDay start, end;

	/**
	 * Creates a new {@link Quarter} for the given start and end {@link MonthDay}.
	 *
	 * @param start must not be {@literal null}.
	 * @param end must not be {@literal null}.
	 */
	private Quarter(MonthDay start, MonthDay end) {

		Assert.notNull(start, "Start MonthDay must not be null!");
		Assert.notNull(end, "End MonthDay must not be null!");

		this.start = start;
		this.end = end;
	}

	/**
	 * Returns the start {@link MonthDay}.
	 *
	 * @return will never be {@literal null}.
	 */
	public MonthDay getStart() {
		return start;
	}

	/**
	 * Returns the end {@link MonthDay}.
	 *
	 * @return will never be {@literal null}.
	 */
	public MonthDay getEnd() {
		return end;
	}

	/**
	 * Returns the next logical {@link Quarter}.
	 *
	 * @return will never be {@literal null}.
	 */
	Quarter next() {

		switch (this) {
			case Q1:
				return Q2;
			case Q2:
				return Q3;
			case Q3:
				return Q4;
			case Q4:
				return Q1;
			default:
				throw new IllegalStateException("¯\\_(ツ)_/¯");
		}
	}
}
