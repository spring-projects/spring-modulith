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
import java.time.ZoneId;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

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

	private final AdjustableClock clock;

	/**
	 * Creates a new {@link TimeMachine} for the given {@link Clock}, {@link ApplicationEventPublisher} and
	 * {@link MomentsProperties}.
	 *
	 * @param clock must not be {@literal null}.
	 * @param events must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	public TimeMachine(Clock clock, ApplicationEventPublisher events, MomentsProperties properties) {
		this(new AdjustableClock(clock), events, properties);
	}

	private TimeMachine(AdjustableClock clock, ApplicationEventPublisher events, MomentsProperties properties) {

		super(clock, events, properties);

		this.clock = clock;
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
	public synchronized TimeMachine skipBy(Duration duration) {

		Assert.notNull(duration, "Duration must not be null!");

		clock.skipBy(duration);

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
	public synchronized TimeMachine skipTo(Instant instant) {

		Assert.notNull(instant, "Instant must not be null!");

		clock.skipBy(Duration.between(instant(), instant));

		return this;
	}

	/**
	 * Resets all shifts and skips applied to the current point in time.
	 *
	 * @see org.springframework.modulith.moments.support.Moments#reset()
	 * @since 2.0.1
	 */
	@Override
	public synchronized Moments reset() {

		clock.reset();

		return super.reset();
	}

	/**
	 * A {@link Clock} that applies an adjustable offset to a delegate {@link Clock}.
	 *
	 * @author Naitik Singh
	 * @since 2.3
	 */
	private static class AdjustableClock extends Clock {

		private final Clock delegate;
		private volatile Duration adjustment = Duration.ZERO;

		/**
		 * Creates a new {@link AdjustableClock} for the given delegate {@link Clock}.
		 *
		 * @param delegate must not be {@literal null}.
		 */
		AdjustableClock(Clock delegate) {

			Assert.notNull(delegate, "Clock must not be null!");

			this.delegate = delegate;
		}

		/**
		 * Adds the given {@link Duration} to the current adjustment. Callers need to synchronize.
		 *
		 * @param duration must not be {@literal null}.
		 */
		void skipBy(Duration duration) {
			this.adjustment = adjustment.plus(duration);
		}

		/**
		 * Wipes the current adjustment. Callers need to synchronize.
		 */
		void reset() {
			this.adjustment = Duration.ZERO;
		}

		/*
		 * (non-Javadoc)
		 * @see java.time.Clock#instant()
		 */
		@Override
		public Instant instant() {
			return delegate.instant().plus(adjustment);
		}

		/*
		 * (non-Javadoc)
		 * @see java.time.Clock#getZone()
		 */
		@Override
		public ZoneId getZone() {
			return delegate.getZone();
		}

		/*
		 * (non-Javadoc)
		 * @see java.time.Clock#withZone(java.time.ZoneId)
		 */
		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.offset(delegate.withZone(zone), adjustment);
		}
	}
}
