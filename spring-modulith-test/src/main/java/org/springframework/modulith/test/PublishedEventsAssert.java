/*
 * Copyright 2022-2023 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import java.util.function.Predicate;

import org.assertj.core.api.AbstractAssert;
import org.springframework.lang.Nullable;
import org.springframework.modulith.test.PublishedEvents.TypedPublishedEvents;
import org.springframework.util.Assert;

/**
 * AssertJ {@link org.assertj.core.api.Assert} for {@link PublishedEvents}.
 *
 * @author Oliver Drotbohm
 * @see AssertablePublishedEvents
 */
public class PublishedEventsAssert extends AbstractAssert<PublishedEventsAssert, PublishedEvents> {

	/**
	 * Creates a new {@link PublishedEventsAssert}
	 *
	 * @param actual must not be {@literal null}.
	 */
	PublishedEventsAssert(AssertablePublishedEvents actual) {
		super(actual, PublishedEventsAssert.class);
	}

	/**
	 * Asserts that the {@link PublishedEvents} contain at least one event of the given type. Use the returned
	 * {@link PublishedEventsAssert} to further qualify the assertions.
	 *
	 * @param <T> the event type expected
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public <T> PublishedEventAssert<T> contains(Class<T> type) {

		Assert.notNull(type, "Type must not be null!");

		var ofType = actual.ofType(type);

		assertThat(ofType).isNotEmpty();

		return new PublishedEventAssert<>(ofType);
	}

	/**
	 * Assertions further qualifying the expected events.
	 *
	 * @author Oliver Drotbohm
	 */
	public class PublishedEventAssert<T> {

		private final TypedPublishedEvents<T> events;

		/**
		 * Creates a new {@link PublishedEventAssert} for the given {@link TypedPublishedEvents}.
		 *
		 * @param events must not be {@literal null}.
		 */
		private PublishedEventAssert(TypedPublishedEvents<T> events) {

			Assert.notNull(events, "TypedPublishedEvents must not be null!");

			this.events = events;
		}

		/**
		 * Asserts that at least one event matches the given predicate.
		 *
		 * @param predicate must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public PublishedEventAssert<T> matching(Predicate<? super T> predicate) {

			Assert.notNull(predicate, "Predicate must not be null!");

			assertThat(events.matching(predicate)).isNotEmpty();

			return this;
		}

		/**
		 * Asserts that at least one event exists for which the value extracted by the given {@link Function} matches the
		 * given {@link Predicate}.
		 *
		 * @param <S> the type of the value to be matched.
		 * @param function the extractor function, must not be {@literal null}.
		 * @param predicate the {@link Predicate} the extracted value is supposed to match. Must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public <S> PublishedEventAssert<T> matching(Function<T, S> function, Predicate<? super S> predicate) {

			Assert.notNull(function, "Function must not be null!");
			Assert.notNull(predicate, "Predicate must not be null!");

			assertThat(events.matching(function, predicate)).isNotEmpty();

			return this;
		}

		/**
		 * Asserts that at least one event exists for which the value extracted by the given {@link Function} matches the
		 * given one.
		 *
		 * @param <S> the type of the value to be matched
		 * @param function the extractor function, must not be {@literal null}.
		 * @param value the expected value, can be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public <S> PublishedEventAssert<T> matching(Function<T, S> function, @Nullable S value) {

			Assert.notNull(function, "Function must not be null!");

			assertThat(events.matching(function, value)).isNotEmpty();

			return this;
		}

		/**
		 * Syntactic sugar to start a new assertion on a different type of event.
		 *
		 * @return will never be {@literal null}.
		 */
		public PublishedEventsAssert and() {
			return PublishedEventsAssert.this;
		}
	}
}
