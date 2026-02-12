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

import java.time.LocalDateTime;
import java.util.Objects;

import org.jmolecules.event.types.DomainEvent;
import org.springframework.util.Assert;

/**
 * A {@link DomainEvent} published on each day.
 *
 * @author Oliver Drotbohm
 */
public class HourHasPassed implements DomainEvent {

	/**
	 * The hour that has just passed.
	 */
	private final LocalDateTime time;

	/**
	 * Creates a new {@link HourHasPassed} for the given {@link LocalDateTime}.
	 *
	 * @param time must not be {@literal null}.
	 */
	private HourHasPassed(LocalDateTime time) {

		Assert.notNull(time, "LocalDateTime must not be null!");

		this.time = time;
	}

	/**
	 * Creates a new {@link HourHasPassed} for the given {@link LocalDateTime}.
	 *
	 * @param time must not be {@literal null}.
	 */
	public static HourHasPassed of(LocalDateTime time) {
		return new HourHasPassed(time);
	}

	/**
	 * The hour that has just passed.
	 *
	 * @return will never be {@literal null}.
	 */
	public LocalDateTime getTime() {
		return time;
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

		if (!(obj instanceof HourHasPassed that)) {
			return false;
		}

		return Objects.equals(time, that.time);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(time);
	}
}
