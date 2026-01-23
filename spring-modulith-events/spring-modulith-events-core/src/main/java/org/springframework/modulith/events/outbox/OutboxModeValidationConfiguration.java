/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.modulith.events.outbox;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.util.ClassUtils;

/**
 * Validation configuration that fails fast if externalization mode is set to {@code outbox} but
 * Namastack Outbox is not on the classpath.
 * <p>
 * This prevents silent failures where events would be configured for outbox externalization but no
 * {@code OutboxHandler} would be registered to actually transport them.
 *
 * @author Roland Beisel
 * @since 2.1
 */
@AutoConfiguration
@AutoConfigureBefore(OutboxEventRecorderAutoConfiguration.class)
@ConditionalOnProperty(name = ExternalizationMode.PROPERTY, havingValue = "outbox")
class OutboxModeValidationConfiguration implements InitializingBean {

	private static final String OUTBOX_CLASS = "io.namastack.outbox.Outbox";

	@Override
	public void afterPropertiesSet() {

		if (!ClassUtils.isPresent(OUTBOX_CLASS, getClass().getClassLoader())) {

			throw new IllegalStateException(
					"Externalization mode is set to 'outbox' but Namastack Outbox is not on the classpath. "
							+ "Please add namastack-outbox to your dependencies.");
		}
	}
}
