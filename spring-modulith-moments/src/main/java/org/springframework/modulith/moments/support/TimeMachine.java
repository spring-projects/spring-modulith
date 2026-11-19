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
package org.springframework.modulith.moments.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Extension of {@link Moments} to publicly expose methods to shift time.
 *
 * @author Oliver Drotbohm
 * @author Naitik Singh
 * @see #now()
 * @see #shiftBy(Duration)
 * @see #skipBy(Duration)
 * @see #skipTo(Instant)
 */
public class TimeMachine extends Moments {

	/**
	 * Creates a new {@link TimeMachine} for the given {@link Clock}, {@link ApplicationEventPublisher} and
	 * {@link MomentsProperties}.
	 *
	 * @param clock must not be {@literal null}.
	 * @param events must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	public TimeMachine(Clock clock, ApplicationEventPublisher events, MomentsProperties properties) {
		super(clock, events, properties);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.moments.support.Moments#now()
	 */
	@Override
	public LocalDateTime now() {
		return super.now();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.moments.support.Moments#shiftBy(java.time.Duration)
	 */
	@Override
	public Moments shiftBy(Duration duration) {
		return super.shiftBy(duration);
	}

	/**
	 * Moves the current point in time by the given {@link Duration} <em>without</em> publishing any events for the time
	 * skipped. A subsequent {@link #shiftBy(Duration)} only publishes the events for the time passing from the new point
	 * in time on. A negative {@link Duration} moves time backwards and, just like {@link #shiftBy(Duration)} with a
	 * negative {@link Duration}, does not publish any events either.
	 *
	 * @param duration must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 * @since 2.3
	 * @see #skipTo(Instant)
	 */
	@Override
	public TimeMachine skipBy(Duration duration) {

		super.skipBy(duration);

		return this;
	}

	/**
	 * Moves the current point in time to the given {@link Instant} <em>without</em> publishing any events for the time
	 * skipped. A subsequent {@link #shiftBy(Duration)} only publishes the events for the time passing from the new point
	 * in time on. An {@link Instant} in the past moves time backwards without publishing any events either. Note that
	 * time keeps advancing from the given {@link Instant} on, according to the underlying {@link Clock}.
	 *
	 * @param instant must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 * @since 2.3
	 * @see #skipBy(Duration)
	 */
	@Override
	public TimeMachine skipTo(Instant instant) {

		super.skipTo(instant);

		return this;
	}

	/**
	 * Resets all shifts and skips applied to the current point in time.
	 *
	 * @see org.springframework.modulith.moments.support.Moments#reset()
	 * @since 2.0.1
	 */
	@Override
	public Moments reset() {
		return super.reset();
	}
}
