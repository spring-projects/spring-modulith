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
package org.springframework.modulith.events;

/**
 * A policy to decide whether an {@link EventPublication} that failed processing should permanently be considered
 * {@link EventPublication.Status#ABANDONED} rather than being resubmitted again. Implementations decide on a
 * per-{@link EventPublication} basis, and can leave the decision to the framework-provided default (a configurable
 * number of resubmission attempts) for any publication they do not want to explicitly rule on.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
public interface AbandonPolicy {

	/**
	 * Returns the {@link Decision} for the given {@link EventPublication}.
	 *
	 * @param publication must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Decision shouldAbandon(EventPublication publication);

	/**
	 * The decision an {@link AbandonPolicy} can make for a particular {@link EventPublication}.
	 *
	 * @author Oliver Drotbohm
	 */
	enum Decision {

		/**
		 * The publication is to be abandoned, i.e. never resubmitted again.
		 */
		ABANDON,

		/**
		 * The publication is to be retained, i.e. kept eligible for resubmission regardless of the framework-provided
		 * default.
		 */
		RETAIN,

		/**
		 * No explicit decision is made; the framework-provided default is applied instead.
		 */
		DEFAULT
	}
}
