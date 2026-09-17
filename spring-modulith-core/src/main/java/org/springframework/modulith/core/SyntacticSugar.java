/*
 * Copyright 2024-2026 the original author or authors.
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
package org.springframework.modulith.core;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates;

/**
 * Helper to make the composition of {@link DescribedPredicate}s more readable.
 *
 * @author Oliver Drotbohm
 * @since 1.2
 */
class SyntacticSugar {

	static Predicate<CanBeAnnotated> isAnnotatedWith(Class<? extends Annotation> type) {
		return isAnnotatedWith(type.getName());
	}

	static Predicate<CanBeAnnotated> isAnnotatedWith(String type) {
		return Predicates.metaAnnotatedWith(type);
	}

	static <T> Predicate<T> are(Predicate<T> predicate) {
		return predicate;
	}

	static <T> Predicate<T> has(Predicate<T> predicate) {
		return predicate;
	}

	static <T> Predicate<T> have(Predicate<T> predicate) {
		return predicate;
	}

	static <T> Predicate<T> is(Predicate<T> predicate) {
		return predicate;
	}

	static <T> Predicate<T> doNotHave(Predicate<T> predicate) {
		return Predicate.not(predicate);
	}
}
