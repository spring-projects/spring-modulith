/*
 * Copyright 2025-2026 the original author or authors.
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
	private final boolean leastRecentlyAttemptedFirst;

	private ResubmissionOptions(int maxInFlight, int batchSize, Duration minAge, Predicate<EventPublication> filter,
			boolean leastRecentlyAttemptedFirst) {

		Assert.isTrue(maxInFlight > 0, "Max in flight number must be greater than zero!");
		Assert.isTrue(batchSize > 0, "Batch size must be greater than zero!");
		Assert.notNull(minAge, "Minimum age must not be null!");
		Assert.isTrue(!minAge.isNegative(), "Minimum age must not be negative!");
		Assert.notNull(filter, "Filter must not be null!");

		this.maxInFlight = maxInFlight;
		this.batchSize = batchSize;
		this.minAge = minAge;
		this.filter = filter;
		this.leastRecentlyAttemptedFirst = leastRecentlyAttemptedFirst;
	}

	/**
	 * Creates a new {@link ResubmissionOptions} with no bound for in-flight publications, a batch size of 100, no minimum
	 * age, including all {@link EventPublication} instances and reading the oldest publications first.
	 *
	 * @return will never be {@literal null}.
	 */
	public static ResubmissionOptions defaults() {
		return new ResubmissionOptions(Integer.MAX_VALUE, 100, Duration.ZERO, __ -> true, false);
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
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter, leastRecentlyAttemptedFirst);
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
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter, leastRecentlyAttemptedFirst);
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
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter, leastRecentlyAttemptedFirst);
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
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter, leastRecentlyAttemptedFirst);
	}

	public boolean isLeastRecentlyAttemptedFirst() {
		return leastRecentlyAttemptedFirst;
	}

	/**
	 * Configures failed {@link EventPublication}s to be read in the order of their last re-submission, falling back to
	 * their publication date for the ones never resubmitted. A publication resubmitted and failed again moves to the end
	 * of the queue, so that publications that keep failing or are skipped by the {@link #withFilter(Predicate) filter}
	 * don't fill every batch read. By default, the oldest publications are read first, which retains their publication
	 * order.
	 *
	 * @return will never be {@literal null}.
	 * @since 2.2
	 */
	public ResubmissionOptions withLeastRecentlyAttemptedFirst() {
		return new ResubmissionOptions(maxInFlight, batchSize, minAge, filter, true);
	}
}
