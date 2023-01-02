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

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link YearHasPassed}.
 *
 * @author Oliver Drotbohm
 */
class YearHasPassedUnitTests {

	@Test
	void returnStartAndEndDateForYear() {

		YearHasPassed event = YearHasPassed.of(2022);

		assertThat(event.getStartDate()).isEqualTo(LocalDate.of(2022, Month.JANUARY, 1));
		assertThat(event.getEndDate()).isEqualTo(LocalDate.of(2022, Month.DECEMBER, 31));
	}
}
