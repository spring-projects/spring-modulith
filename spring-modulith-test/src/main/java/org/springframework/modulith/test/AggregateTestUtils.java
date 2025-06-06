/*
 * Copyright 2020-2025 the original author or authors.
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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.DomainEvents;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;

/**
 * Test utilities to work with aggregates.
 *
 * @author Oliver Drotbohm
 */
public class AggregateTestUtils {

	private static Map<Class<?>, Optional<Method>> CACHE = new ConcurrentHashMap<>();

	/**
	 * Extracts all domain events from the given aggregate that uses Spring Data's {@link DomainEvents} annotation to
	 * expose them.
	 *
	 * @param aggregate must not be {@literal null}.
	 * @return {@link PublishedEvents} for all events contained in the given aggregate, will never be {@literal null}.
	 */
	public static PublishedEvents eventsOf(Object aggregate) {

		Collection<?> events = CACHE.computeIfAbsent(aggregate.getClass(), AggregateTestUtils::findAnnotatedMethod)
				.map(it -> ReflectionUtils.invokeMethod(it, aggregate)) //
				.map(Collection.class::cast) //
				.orElseGet(Collections::emptyList);

		return PublishedEvents.of(events);
	}

	private static Optional<Method> findAnnotatedMethod(Class<?> type) {

		DomainEventsMethodFinder finder = new DomainEventsMethodFinder();
		ReflectionUtils.doWithMethods(type, finder);

		return Optional.ofNullable(finder.method);
	}

	/**
	 * {@link MethodCallback} to find a method annotated with {@link DomainEvents}.
	 *
	 * @author Oliver Drotbohm
	 */
	private static class DomainEventsMethodFinder implements MethodCallback {

		@Nullable Method method;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.util.ReflectionUtils.MethodCallback#doWith(java.lang.reflect.Method)
		 */
		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {

			if (this.method != null) {
				return;
			}

			if (method.isAnnotationPresent(DomainEvents.class)) {
				this.method = method;
				ReflectionUtils.makeAccessible(method);
			}
		}
	}
}
