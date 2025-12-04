/*
 * Copyright 2022-2025 the original author or authors.
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
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Extension of {@link Moments} to publicly expose methods to shift time.
 *
 * @author Oliver Drotbohm
 * @see #now()
 * @see #shiftBy(Duration)
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
	 * @see org.springframework.modulith.moments.support.Moments#reset()
	 * @since 2.0.1
	 */
	@Override
	public Moments reset() {
		return super.reset();
	}
}
