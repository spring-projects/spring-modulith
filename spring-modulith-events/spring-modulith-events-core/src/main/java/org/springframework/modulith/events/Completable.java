/*
 * Copyright 2023 the original author or authors.
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

import java.time.Instant;

/**
 * Internal interface to be able to mark {@link EventPublication} instances as completed.
 *
 * @author Oliver Drotbohm
 */
interface Completable {

	/**
	 * Marks the instance as completed at the given {@link Instant}.
	 *
	 * @param instant must not be {@literal null}.
	 */
	void markCompleted(Instant instant);
}
