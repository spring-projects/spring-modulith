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
package org.springframework.modulith.events.core;

import static org.springframework.modulith.events.AbandonPolicy.Decision.*;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.modulith.events.AbandonPolicy;
import org.springframework.modulith.events.EventPublication;
import org.springframework.util.Assert;

/**
 * Resolves the effective abandon decision for an {@link EventPublication} by combining zero or more user-supplied
 * {@link AbandonPolicy} instances with the framework-provided default (a configurable number of resubmission attempts).
 * <p>
 * The user-supplied policies are consulted in order (see {@link org.springframework.core.Ordered} /
 * {@link org.springframework.core.annotation.Order} to control that order across multiple beans). The first one to
 * return {@link AbandonPolicy.Decision#ABANDON} or {@link AbandonPolicy.Decision#RETAIN} short-circuits the remaining
 * policies as well as the default. If every policy returns {@link AbandonPolicy.Decision#DEFAULT} (or none are
 * configured at all), the default is applied.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
public class AbandonPolicies {

	private final List<AbandonPolicy> userPolicies;
	private final Predicate<EventPublication> defaultPolicy;

	/**
	 * Creates a new {@link AbandonPolicies} for the given user-supplied {@link AbandonPolicy} instances and default
	 * policy.
	 *
	 * @param userPolicies must not be {@literal null}.
	 * @param defaultPolicy must not be {@literal null}.
	 */
	public AbandonPolicies(List<AbandonPolicy> userPolicies, Predicate<EventPublication> defaultPolicy) {

		Assert.notNull(userPolicies, "User-supplied AbandonPolicies must not be null!");
		Assert.notNull(defaultPolicy, "Default AbandonPolicy must not be null!");

		this.userPolicies = List.copyOf(userPolicies);
		this.defaultPolicy = defaultPolicy;
	}

	/**
	 * Returns an {@link AbandonPolicies} instance that never abandons a publication, i.e. neither a user policy nor a
	 * default is in effect.
	 *
	 * @return will never be {@literal null}.
	 */
	public static AbandonPolicies none() {
		return new AbandonPolicies(List.of(), __ -> false);
	}

	/**
	 * Returns a new {@link AbandonPolicies} that uses only the given {@link AbandonPolicy} instead of the configured
	 * user-supplied ones, while retaining the framework-provided default as the fallback for
	 * {@link AbandonPolicy.Decision#DEFAULT} decisions.
	 *
	 * @param policy must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public AbandonPolicies withPolicy(AbandonPolicy policy) {

		Assert.notNull(policy, "AbandonPolicy must not be null!");

		return new AbandonPolicies(List.of(policy), defaultPolicy);
	}

	/**
	 * Returns whether the given {@link EventPublication} should be abandoned.
	 *
	 * @param publication must not be {@literal null}.
	 */
	public boolean shouldAbandon(EventPublication publication) {

		Assert.notNull(publication, "EventPublication must not be null!");

		for (var policy : userPolicies) {

			var decision = policy.shouldAbandon(publication);

			if (decision == ABANDON) {
				return true;
			}

			if (decision == RETAIN) {
				return false;
			}
		}

		return defaultPolicy.test(publication);
	}
}
