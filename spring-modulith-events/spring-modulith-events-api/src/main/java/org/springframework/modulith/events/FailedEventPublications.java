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

/**
 * All uncompleted event publications.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
public interface FailedEventPublications {

	/**
	 * Initiate the re-submission of failed {@link EventPublication} according to the given {@link ResubmissionOptions}.
	 *
	 * @param options must not be {@literal null}.
	 */
	void resubmit(ResubmissionOptions options);

	/**
	 * Applies the globally configured {@link AbandonPolicy} beans (and framework-provided default) to all currently
	 * failed {@link EventPublication}s, marking the ones it decides to give up on as
	 * {@link EventPublication.Status#ABANDONED}.
	 * <p>
	 * This is an explicit, on-demand operation. It is not run automatically and, in particular, does not retroactively
	 * abandon anything unless invoked. Use it, for example, right after registering a new {@link AbandonPolicy} to
	 * apply it to publications that already failed before that policy existed.
	 *
	 * @since 2.2
	 * @see #applyAbandonPolicy(AbandonPolicy)
	 */
	void applyAbandonPolicy();

	/**
	 * Applies the given {@link AbandonPolicy} -- instead of the globally configured ones -- to all currently failed
	 * {@link EventPublication}s, marking the ones it decides to give up on as {@link EventPublication.Status#ABANDONED}.
	 * The framework-provided default (based on the configured resubmission attempts) still applies as the fallback for
	 * {@link AbandonPolicy.Decision#DEFAULT} decisions.
	 * <p>
	 * This is an explicit, on-demand operation. It is not run automatically and, in particular, does not retroactively
	 * abandon anything unless invoked.
	 *
	 * @param policy must not be {@literal null}.
	 * @since 2.2
	 * @see #applyAbandonPolicy()
	 */
	void applyAbandonPolicy(AbandonPolicy policy);
}
