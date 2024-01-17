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
import java.util.Objects;

import org.jmolecules.event.types.DomainEvent;
import org.springframework.util.Assert;

/**
 * A {@link DomainEvent} published on each day.
 *
 * @author Oliver Drotbohm
 */
public class DayHasPassed implements DomainEvent {

	/**
	 * The day that has just passed.
	 */
	private final LocalDate date;

	/**
	 * Creates a new {@link DayHasPassed} for the given {@link LocalDate}.
	 *
	 * @param date must not be {@literal null}.
	 */
	private DayHasPassed(LocalDate date) {

		Assert.notNull(date, "LocalDate must not be null!");

		this.date = date;
	}

	/**
	 * Creates a new {@link DayHasPassed} for the given {@link LocalDate}.
	 *
	 * @param date must not be {@literal null}.
	 */
	public static DayHasPassed of(LocalDate date) {
		return new DayHasPassed(date);
	}

	/**
	 * The day that has just passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public LocalDate getDate() {
		return date;
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

		if (!(obj instanceof DayHasPassed that)) {
			return false;
		}

		return Objects.equals(date, that.date);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(date);
	}
}
