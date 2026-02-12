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

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuarterHasPassed}.
 *
 * @author Oliver Drotbohm
 */
class QuarterHasPassedUnitTests {

	@Test
	void calculatesStartAndEndDateOfQuarter() {

		QuarterHasPassed event = QuarterHasPassed.of(Year.of(2022), Quarter.Q1);

		assertThat(event.getStartDate()).isEqualTo(LocalDate.of(2022, 1, 1));
		assertThat(event.getEndDate()).isEqualTo(LocalDate.of(2022, 3, 31));
	}

	@Test
	void calculatesStartAndEndDateForShiftedQuarter() {

		QuarterHasPassed event = QuarterHasPassed.of(Year.of(2022), Quarter.Q1, Month.FEBRUARY);

		assertThat(event.getStartDate()).isEqualTo(LocalDate.of(2022, 2, 1));
		assertThat(event.getEndDate()).isEqualTo(LocalDate.of(2022, 4, 30));
	}
}
