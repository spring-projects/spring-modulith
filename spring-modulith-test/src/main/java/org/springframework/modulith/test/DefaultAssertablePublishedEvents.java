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
package org.springframework.modulith.test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link AssertablePublishedEvents}.
 *
 * @author Oliver Drotbohm
 */
class DefaultAssertablePublishedEvents implements AssertablePublishedEvents, ApplicationListener<ApplicationEvent> {

	private final DefaultPublishedEvents delegate;

	/**
	 * Creates a new {@link DefaultAssertablePublishedEvents} with the given {@link DefaultPublishedEvents} delegate.
	 *
	 * @param delegate must not be {@literal null}.
	 */
	DefaultAssertablePublishedEvents(DefaultPublishedEvents delegate) {

		Assert.notNull(delegate, "DefaultPublishedEvents must not be null!");

		this.delegate = delegate;
	}

	/**
	 * Creates a new {@link DefaultAssertablePublishedEvents}.
	 */
	DefaultAssertablePublishedEvents() {
		this(new DefaultPublishedEvents());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.test.PublishedEvents#ofType(java.lang.Class)
	 */
	@Override
	public <T> TypedPublishedEvents<T> ofType(Class<T> type) {
		return delegate.ofType(type);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		delegate.onApplicationEvent(event);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return delegate.toString();
	}
}
