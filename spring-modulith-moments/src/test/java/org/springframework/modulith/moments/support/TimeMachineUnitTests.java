/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.moments.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.modulith.moments.HourHasPassed;
import org.springframework.modulith.moments.MonthHasPassed;
import org.springframework.modulith.moments.QuarterHasPassed;
import org.springframework.modulith.moments.YearHasPassed;

/**
 * Unit tests for {@link TimeMachine}.
 *
 * @author Naitik Singh
 */
class TimeMachineUnitTests {

	static final Instant START = Instant.parse("2026-05-17T10:00:00Z");

	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	TimeMachine timeMachine = new TimeMachine(Clock.fixed(START, ZoneOffset.UTC), events, MomentsProperties.DEFAULTS);

	@Test // GH-2021
	void skipByMovesTimeWithoutPublishingEvents() {

		timeMachine.skipBy(Duration.ofDays(400));

		assertThat(timeMachine.instant()).isEqualTo(START.plus(Duration.ofDays(400)));
		verifyNoInteractions(events);
	}

	@Test // GH-2021
	void skipToMovesTimeWithoutPublishingEvents() {

		var target = Instant.parse("2027-12-31T23:30:00Z");

		timeMachine.skipTo(target);

		assertThat(timeMachine.instant()).isEqualTo(target);
		verifyNoInteractions(events);
	}

	@Test // GH-2021
	void nowReflectsSkippedTime() {

		Now now = timeMachine.skipTo(Instant.parse("2027-03-15T20:00:00Z"));

		assertThat(now.now()).isEqualTo(LocalDateTime.of(2027, 3, 15, 20, 0));
		assertThat(now.today()).isEqualTo(LocalDate.of(2027, 3, 15));
		assertThat(now.instant()).isEqualTo(Instant.parse("2027-03-15T20:00:00Z"));
	}

	@Test // GH-2021
	void shiftAfterSkipOnlyPublishesEventsFromTheNewPointInTime() {

		timeMachine.skipTo(Instant.parse("2027-12-31T23:30:00Z"));
		timeMachine.shiftBy(Duration.ofHours(1));

		verify(events).publishEvent(HourHasPassed.of(LocalDateTime.of(2027, 12, 31, 23, 0)));
		verify(events).publishEvent(DayHasPassed.of(LocalDate.of(2027, 12, 31)));
		verify(events).publishEvent(MonthHasPassed.of(YearMonth.of(2027, 12)));
		verify(events).publishEvent(YearHasPassed.of(Year.of(2027)));
		verify(events, times(1)).publishEvent(any(HourHasPassed.class));
		verify(events, times(1)).publishEvent(any(DayHasPassed.class));
		verify(events, times(1)).publishEvent(any(MonthHasPassed.class));
		verify(events, times(1)).publishEvent(any(QuarterHasPassed.class));
		verify(events, times(1)).publishEvent(any(YearHasPassed.class));

		assertThat(timeMachine.instant()).isEqualTo(Instant.parse("2028-01-01T00:30:00Z"));
	}

	@Test // GH-2021
	void skipToTakesPreviousShiftsIntoAccount() {

		var target = Instant.parse("2027-01-01T00:00:00Z");

		timeMachine.shiftBy(Duration.ofHours(2));
		reset(events);

		timeMachine.skipTo(target);

		assertThat(timeMachine.instant()).isEqualTo(target);
		verifyNoInteractions(events);
	}

	@Test // GH-2021
	void skipsAccumulate() {

		timeMachine.skipBy(Duration.ofDays(1)).skipBy(Duration.ofHours(1));

		assertThat(timeMachine.instant()).isEqualTo(START.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));
	}

	@Test // GH-2021
	void skipByNegativeDurationMovesTimeBackwardsWithoutPublishingEvents() {

		timeMachine.skipBy(Duration.ofDays(-1));

		assertThat(timeMachine.instant()).isEqualTo(START.minus(Duration.ofDays(1)));
		verifyNoInteractions(events);
	}

	@Test // GH-2021
	void skipToInstantInThePastMovesTimeBackwardsWithoutPublishingEvents() {

		var target = Instant.parse("2020-01-01T00:00:00Z");

		timeMachine.skipTo(target);

		assertThat(timeMachine.instant()).isEqualTo(target);
		verifyNoInteractions(events);
	}

	@Test // GH-2021
	void skipByZeroDurationDoesNotChangeTime() {

		timeMachine.skipBy(Duration.ZERO);

		assertThat(timeMachine.instant()).isEqualTo(START);
		verifyNoInteractions(events);
	}

	@Test // GH-2021
	void resetWipesSkipsAndShifts() {

		timeMachine.skipBy(Duration.ofDays(30));
		timeMachine.shiftBy(Duration.ofHours(1));
		timeMachine.skipTo(Instant.parse("2030-01-01T00:00:00Z"));

		timeMachine.reset();

		assertThat(timeMachine.instant()).isEqualTo(START);
	}

	@Test // GH-2021
	void rejectsNullArguments() {

		assertThatIllegalArgumentException().isThrownBy(() -> timeMachine.skipBy(null));
		assertThatIllegalArgumentException().isThrownBy(() -> timeMachine.skipTo(null));
	}
}
