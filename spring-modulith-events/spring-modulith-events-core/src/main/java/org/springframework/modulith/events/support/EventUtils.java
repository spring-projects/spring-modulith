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
package org.springframework.modulith.events.support;

import java.time.Duration;

import org.springframework.util.Assert;

/**
 * General utilities.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 */
public class EventUtils {

	/**
	 * Creates a human-readable {@link String} from a {@link Duration}.
	 *
	 * @param duration must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static String prettyPrint(Duration duration) {

		Assert.notNull(duration, "Duration must not be null!");

		return duration.toString()
				.substring(2)
				.replaceAll("(\\d[HMS])(?!$)", "$1 ")
				.toLowerCase();
	}
}
