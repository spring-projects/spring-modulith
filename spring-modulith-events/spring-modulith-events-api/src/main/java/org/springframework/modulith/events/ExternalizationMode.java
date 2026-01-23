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

import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * Different modes of event externalization.
 *
 * @author Roland Beisel
 * @since 2.1
 */
public enum ExternalizationMode {

	/**
	 * Externalizes events via a module listener that sends events to the target after the transaction commits. This is
	 * the default behavior.
	 */
	MODULE_LISTENER,

	/**
	 * Externalizes events via the outbox pattern. Events are persisted to an outbox table within the same transaction and
	 * later processed asynchronously.
	 */
	OUTBOX;

	public static final String PROPERTY = "spring.modulith.events.externalization.mode";

	/**
	 * Looks up the {@link ExternalizationMode} from the given environment or uses {@link #MODULE_LISTENER} as default.
	 *
	 * @param environment must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ExternalizationMode from(Environment environment) {

		Assert.notNull(environment, "Environment must not be null!");

		var result = environment.getProperty(PROPERTY, ExternalizationMode.class);

		return result == null ? ExternalizationMode.MODULE_LISTENER : result;
	}
}
