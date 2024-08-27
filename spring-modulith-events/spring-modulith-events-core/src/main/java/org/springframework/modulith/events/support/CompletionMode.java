/*
 * Copyright 2024 the original author or authors.
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
 * Different modes of event completion.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 * @soundtrack Lettuce - Waffles (Unify)
 */
public enum CompletionMode {

	/**
	 * Completes an {@link org.springframework.modulith.events.EventPublication} by setting its completion date and
	 * updating the database entry accordingly.
	 */
	UPDATE,

	/**
	 * Completes an {@link org.springframework.modulith.events.EventPublication} by removing the database entry.
	 */
	DELETE;

	public static final String PROPERTY = "spring.modulith.events.completion-mode";

	/**
	 * Looks up the {@link CompletionMode} from the given environment or uses {@link #UPDATE} as default.
	 *
	 * @param environment must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static CompletionMode from(Environment environment) {

		Assert.notNull(environment, "Environment must not be null!");

		var result = environment.getProperty(PROPERTY, CompletionMode.class);

		return result == null ? CompletionMode.UPDATE : result;
	}
}
