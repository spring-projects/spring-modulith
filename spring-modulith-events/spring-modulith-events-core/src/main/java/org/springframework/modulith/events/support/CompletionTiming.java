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
package org.springframework.modulith.events.support;

import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * Defines when the completion of an event publication takes place relative to the transactional event listener.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
public enum CompletionTiming {

	/**
	 * Completes the event publication within the listener's transaction, right before it commits. Completion is atomic
	 * with the listener's work. This is the default behavior.
	 */
	IN_LISTENER,

	/**
	 * Completes the event publication in a transaction of its own after the listener (and its transaction) has finished.
	 */
	IN_INTERCEPTOR;

	public static final String PROPERTY = "spring.modulith.events.completion";

	/**
	 * Looks up the {@link CompletionTiming} from the given environment or uses {@link #IN_LISTENER} as default.
	 *
	 * @param environment must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static CompletionTiming from(Environment environment) {

		Assert.notNull(environment, "Environment must not be null!");

		var result = environment.getProperty(PROPERTY, CompletionTiming.class);

		return result == null ? IN_LISTENER : result;
	}
}
