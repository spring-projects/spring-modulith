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
package org.springframework.modulith.test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ClassUtils;

/**
 * Creates an instance of {@link PublishedEvents} or {@link AssertablePublishedEvents} depending on whether AssertJ is
 * on the classpath or not.
 *
 * @author Oliver Drotbohm
 * @since 1.4
 */
class PublishedEventsFactory {

	private static final boolean ASSERT_J_PRESENT = ClassUtils.isPresent("org.assertj.core.api.Assert",
			PublishedEventsParameterResolver.class.getClassLoader());

	/**
	 * Returns whether AssertJ is present or not.
	 */
	static boolean isAssertJPresent() {
		return ASSERT_J_PRESENT;
	}

	/**
	 * Creates a instance of {@link PublishedEvents} that's also an {@link ApplicationListener} for
	 * {@link ApplicationEvent}s.
	 *
	 * @return will never be {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	static <T extends PublishedEvents & ApplicationListener<ApplicationEvent>> T createPublishedEvents() {
		return (T) (isAssertJPresent() ? new DefaultAssertablePublishedEvents() : new DefaultPublishedEvents());
	}
}
