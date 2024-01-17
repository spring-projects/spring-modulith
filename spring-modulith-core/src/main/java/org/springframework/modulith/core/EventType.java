/*
 * Copyright 2020-2024 the original author or authors.
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
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * A type that represents an event in a system.
 *
 * @author Oliver Drotbohm
 */
public class EventType {

	private final JavaClass type;
	private final List<Source> sources;

	/**
	 * Creates a new {@link EventType} for the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 */
	public EventType(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		this.type = type;

		var factoryMethodCalls = type.getMethods().stream()
				.filter(method -> method.getModifiers().contains(JavaModifier.STATIC))
				.filter(method -> method.getRawReturnType().equals(type))
				.flatMap(method -> method.getCallsOfSelf().stream());

		var constructorCalls = type.getConstructors().stream()
				.flatMap(constructor -> constructor.getCallsOfSelf().stream());

		this.sources = Stream.concat(constructorCalls, factoryMethodCalls)
				.filter(call -> !call.getOriginOwner().equals(type))
				.<Source> map(JavaAccessSource::new)
				.toList();
	}

	/**
	 * The {@link JavaClass} of the {@link EventType}.
	 *
	 * @return will never be {@literal null}.
	 */
	public JavaClass getType() {
		return type;
	}

	/**
	 * The sources that create that event. Includes static factory methods that return an instance of the event type
	 * itself as well as constructor invocations, except ones from the factory methods.
	 *
	 * @return will never be {@literal null}.
	 */
	public List<Source> getSources() {
		return sources;
	}

	/**
	 * Whether any sources exist at all.
	 *
	 * @see #getSources()
	 */
	public boolean hasSources() {
		return !this.sources.isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "EventType [type=" + type + ", sources=" + sources + "]";
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof EventType thaType)) {
			return false;
		}

		return Objects.equals(sources, thaType.sources) //
				&& Objects.equals(type, thaType.type);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(sources, type);
	}
}
