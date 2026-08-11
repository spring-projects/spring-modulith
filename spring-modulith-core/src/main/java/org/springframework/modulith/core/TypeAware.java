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
package org.springframework.modulith.core;

import java.util.List;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * A type that revolves around a single, well-known {@link JavaClass}, e.g. exposing where instances of it get
 * created.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
interface TypeAware {

	/**
	 * Returns the underlying {@link JavaClass}.
	 *
	 * @return will never be {@literal null}.
	 */
	JavaClass getType();

	/**
	 * Returns all {@link JavaCall}s creating an instance of {@link #getType()}, i.e. either invocations of a
	 * constructor or of a static factory method returning the type itself.
	 *
	 * @return will never be {@literal null}.
	 */
	default List<JavaCall<?>> getCreations() {

		var type = getType();

		var factoryMethodCalls = type.getMethods().stream()
				.filter(method -> method.getModifiers().contains(JavaModifier.STATIC))
				.filter(method -> method.getRawReturnType().equals(type))
				.flatMap(method -> method.getCallsOfSelf().stream());

		var constructorCalls = type.getConstructors().stream()
				.flatMap(constructor -> constructor.getCallsOfSelf().stream());

		return Stream.<JavaCall<?>> concat(constructorCalls, factoryMethodCalls).toList();
	}
}
