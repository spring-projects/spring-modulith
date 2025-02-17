/*
 * Copyright 2019-2025 the original author or authors.
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

import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;

/**
 * Provides instances of {@link PublishedEvents} as test method parameters.
 *
 * @author Oliver Drotbohm
 */
class PublishedEventsParameterResolver implements ParameterResolver, AfterEachCallback {

	private final Function<ExtensionContext, ApplicationContext> lookup;
	private @Nullable ThreadBoundApplicationListenerAdapter listener;

	PublishedEventsParameterResolver() {
		this(ctx -> SpringExtension.getApplicationContext(ctx));
	}

	PublishedEventsParameterResolver(Function<ExtensionContext, ApplicationContext> supplier) {
		this.lookup = supplier;
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.ParameterResolver#supportsParameter(org.junit.jupiter.api.extension.ParameterContext, org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		var type = parameterContext.getParameter().getType();

		if (type.getName().equals("org.springframework.modulith.test.AssertablePublishedEvents")
				&& !PublishedEventsFactory.isAssertJPresent()) {
			throw new IllegalStateException(
					"Method declares AssertablePublishedEvents as parameter but AssertJ is not on the classpath!");
		}

		return PublishedEvents.class.isAssignableFrom(type);
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.ParameterResolver#resolveParameter(org.junit.jupiter.api.extension.ParameterContext, org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public PublishedEvents resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {

		var publishedEvents = PublishedEventsFactory.createPublishedEvents();

		initializeListener(extensionContext);

		if (listener != null) {
			listener.registerDelegate(publishedEvents);
		}

		return publishedEvents;
	}

	private void initializeListener(ExtensionContext extensionContext) {

		if (listener != null) {
			return;
		}

		ApplicationContext context = lookup.apply(extensionContext);

		if (!(context instanceof AbstractApplicationContext aac)) {
			throw new IllegalStateException();
		}

		listener = aac.getApplicationListeners().stream()
				.filter(ThreadBoundApplicationListenerAdapter.class::isInstance)
				.map(ThreadBoundApplicationListenerAdapter.class::cast)
				.findFirst()
				.orElseGet(() -> {

					var adapter = new ThreadBoundApplicationListenerAdapter();
					aac.addApplicationListener(adapter);

					return adapter;
				});
	}

	/*
	 * (non-Javadoc)
	 * @see org.junit.jupiter.api.extension.AfterEachCallback#afterEach(org.junit.jupiter.api.extension.ExtensionContext)
	 */
	@Override
	public void afterEach(ExtensionContext context) {

		if (listener != null) {
			listener.unregisterDelegate();
		}
	}

	/**
	 * {@link ApplicationListener} that allows registering delegate {@link ApplicationListener}s that are held in a
	 * {@link ThreadLocal} and get used on {@link #onApplicationEvent(ApplicationEvent)} if one is registered for the
	 * current thread. This allows multiple event listeners to see the events fired in a certain thread in a concurrent
	 * execution scenario.
	 *
	 * @author Oliver Drotbohm
	 */
	private static class ThreadBoundApplicationListenerAdapter implements ApplicationListener<ApplicationEvent> {

		private final ThreadLocal<ApplicationListener<ApplicationEvent>> delegate = new InheritableThreadLocal<>();

		/**
		 * Registers the given {@link ApplicationListener} to be used for the current thread.
		 *
		 * @param listener must not be {@literal null}.
		 */
		void registerDelegate(ApplicationListener<ApplicationEvent> listener) {

			Assert.notNull(listener, "Delegate ApplicationListener must not be null!");

			delegate.set(listener);
		}

		/**
		 * Removes the registration of the currently assigned {@link ApplicationListener}.
		 */
		void unregisterDelegate() {
			delegate.remove();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
		 */
		@Override
		public void onApplicationEvent(ApplicationEvent event) {

			ApplicationListener<ApplicationEvent> listener = delegate.get();

			if (listener != null) {
				listener.onApplicationEvent(event);
			}
		}
	}
}
