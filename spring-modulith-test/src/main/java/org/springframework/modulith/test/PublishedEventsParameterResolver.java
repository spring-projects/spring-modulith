/*
 * Copyright 2019-2026 the original author or authors.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.modulith.test.PublishedEventsFactory.PublishedEventsListenerAdapter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;

/**
 * Provides instances of {@link PublishedEvents} as test method parameters.
 *
 * @author Oliver Drotbohm
 */
class PublishedEventsParameterResolver implements ParameterResolver, AfterEachCallback {

	private final Function<ExtensionContext, ApplicationContext> lookup;

	private @Nullable InternalPublishedEventsFactory factory;

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
	public PublishedEvents resolveParameter(ParameterContext parameterContext, ExtensionContext context) {

		if (factory != null) {
			return factory.createPublishedEvents(context);
		}

		var applicationContext = lookup.apply(context);
		var environment = applicationContext.getEnvironment();

		var threadBound = environment.getProperty("spring.modulith.test.thread-bound-published-events", Boolean.class,
				false);

		this.factory = threadBound
				? new ThreadBoundPublishedEventsFactory()
				: detectOrCreate(SingletonPublishedEventsFactory.class, SingletonPublishedEventsFactory::new, context);

		return factory.createPublishedEvents(context);
	}

	/**
	 * Looks up the bean of the given type if present or creates one through the given factory and registers it as
	 * application listener in the {@link ApplicationContext} backing the {@link ExtensionContext}.
	 *
	 * @return will never be {@literal null}.
	 */
	private <T extends ApplicationListener<ApplicationEvent>> T detectOrCreate(Class<T> type,
			Supplier<? extends T> factory, ExtensionContext extensionContext) {

		var context = lookup.apply(extensionContext);

		if (!(context instanceof AbstractApplicationContext aac)) {
			throw new IllegalStateException();
		}

		return aac.getApplicationListeners().stream()
				.filter(type::isInstance)
				.map(type::cast)
				.findFirst()
				.orElseGet(() -> {

					var events = factory.get();
					aac.addApplicationListener(events);

					return events;
				});
	}

	private interface InternalPublishedEventsFactory extends AfterEachCallback {

		PublishedEvents createPublishedEvents(ExtensionContext context);

		@Override
		default void afterEach(ExtensionContext context) {}
	}

	private class ThreadBoundPublishedEventsFactory implements InternalPublishedEventsFactory {

		private @Nullable ThreadBoundApplicationListenerAdapter listener;

		public PublishedEvents createPublishedEvents(ExtensionContext context) {

			var publishedEvents = PublishedEventsFactory.createPublishedEvents();

			initializeListener(context);

			if (listener != null) {
				listener.registerDelegate(publishedEvents);
			}

			return publishedEvents;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.test.PublishedEventsParameterResolver.InternalPublishedEventsFactory#afterEach(org.junit.jupiter.api.extension.ExtensionContext)
		 */
		@Override
		public void afterEach(ExtensionContext context) {

			if (listener != null) {
				listener.unregisterDelegate();
			}
		}

		private void initializeListener(ExtensionContext extensionContext) {

			if (listener != null) {
				return;
			}

			this.listener = detectOrCreate(ThreadBoundApplicationListenerAdapter.class,
					ThreadBoundApplicationListenerAdapter::new,
					extensionContext);
		}
	}

	private class SingletonPublishedEventsFactory
			implements InternalPublishedEventsFactory, ApplicationListener<ApplicationEvent> {

		private final Map<String, PublishedEventsListenerAdapter> events = new ConcurrentHashMap<>();

		public PublishedEvents createPublishedEvents(ExtensionContext context) {
			return events.computeIfAbsent(context.getUniqueId(), __ -> PublishedEventsFactory.createPublishedEvents());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
		 */
		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			events.values().forEach(it -> it.onApplicationEvent(event));
		}

		/*
		 *
		 * (non-Javadoc)
		 * @see org.springframework.modulith.test.PublishedEventsParameterResolver.InternalPublishedEventsFactory#afterEach(org.junit.jupiter.api.extension.ExtensionContext)
		 */
		@Override
		public void afterEach(ExtensionContext context) {
			events.remove(context.getUniqueId());
		}
	}

	@Override
	public void afterEach(ExtensionContext context) {

		if (factory != null) {
			factory.afterEach(context);
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
