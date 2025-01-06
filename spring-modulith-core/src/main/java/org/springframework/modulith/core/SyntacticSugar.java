/*
 * Copyright 2024-2025 the original author or authors.
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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates;

/**
 * Helper to make the composition of {@link DescribedPredicate}s more readable.
 *
 * @author Oliver Drotbohm
 * @since 1.2
 */
class SyntacticSugar {

	static DescribedPredicate<CanBeAnnotated> isAnnotatedWith(Class<? extends Annotation> type) {
		return isAnnotatedWith(type.getName());
	}

	static DescribedPredicate<CanBeAnnotated> isAnnotatedWith(String type) {
		return Predicates.metaAnnotatedWith(type);
	}

	static <T> DescribedPredicate<T> are(DescribedPredicate<T> predicate) {
		return predicate;
	}

	static <T> DescribedPredicate<T> has(DescribedPredicate<T> predicate) {
		return predicate;
	}

	static <T> DescribedPredicate<T> have(DescribedPredicate<T> predicate) {
		return predicate;
	}

	static <T> DescribedPredicate<T> is(DescribedPredicate<T> predicate) {
		return predicate;
	}

	static <T> DescribedPredicate<T> doNotHave(DescribedPredicate<T> predicate) {
		return DescribedPredicate.not(predicate);
	}
}
