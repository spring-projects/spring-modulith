/*
 * Copyright 2025 the original author or authors.
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
package org.springframework.modulith.events;

import java.time.Duration;
import java.util.function.Predicate;

import org.springframework.util.Assert;

/**
 * Options to be considered during {@link org.springframework.modulith.events.EventPublication} re-submission.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
public class ResubmissionOptions {

	private final int maxInFlight;
	private final int batchSize;
	private final Duration minAge;
	private final Predicate<EventPublication> filter;

	private ResubmissionOptions(int maxInFlight, int batchSize, Duration minAge, Predicate<EventPublication> filter) {

		Assert.isTrue(maxInFlight > 0, "Max in flight number must be greater than zero!");
		Assert.isTrue(batchSize > 0, "Batch size must be greater than zero!");
		Assert.notNull(minAge, "Minimum age must not be null!");
		Assert.isTrue(!minAge.isNegative(), "Minimum age must not be negative!");
		Assert.notNull(filter, "Filter must not be null!");

		this.maxInFlight = maxInFlight;
		this.batchSize = batchSize;
		this.minAge = minAge;
		this.filter = filter;
	}

	/**
	 * Creates a new {@link ResubmissionOptions} with no bound for in-flight publications, a batch size of 100, no minimum
	 * age and including all {@link EventPublication} instances.
	 *
	 * @return will never be {@literal null}.
	 */
	public static ResubmissionOptions defaults() {
		return new ResubmissionOptions(Integer.MAX_VALUE, 100, Duration.ZERO, __ -> true);
	}

	public int getMaxInFlight() {
		return maxInFlight;
	}

	/**
	 * Configures the number of publications that are supposed to be in flight concurrently. This means that for each
	 * re-submission attempt, only a number less than or equal to the configured value will be resubmitted.
	 *
	 * @param maxInFlight must not be less than or equal to zero.
	 * @return will never be {@literal null}.
	 */
	public ResubmissionOptions withMaxInFlight(int maxInFlight) {
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter);
	}

	public int getBatchSize() {
		return batchSize;
	}

	/**
	 * Configures the batch size with which to read publications from the database.
	 *
	 * @param batchSize must not be less than or equal to zero.
	 * @return will never be {@literal null}.
	 */
	public ResubmissionOptions withBatchSize(int batchSize) {
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter);
	}

	public Duration getMinAge() {
		return minAge;
	}

	/**
	 * Configures the minimum age of event publications to qualify for re-submission.
	 *
	 * @param minAge must not {@literal null} be negative.
	 * @return will never be {@literal null}.
	 */
	public ResubmissionOptions withMinAge(Duration minAge) {
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter);
	}

	public Predicate<EventPublication> getFilter() {
		return filter;
	}

	/**
	 * Configures which {@link EventPublication}s to resubmit in a re-submission attempt.
	 *
	 * @param filter must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public ResubmissionOptions withFilter(Predicate<EventPublication> filter) {
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter);
	}
}
