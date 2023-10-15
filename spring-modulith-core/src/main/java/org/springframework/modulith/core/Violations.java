/*
 * Copyright 2020-2023 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * Value type to gather and report architectural violations.
 *
 * @author Oliver Drotbohm
 */
public class Violations extends RuntimeException {

	private static final long serialVersionUID = 6863781504675034691L;

	public static Violations NONE = new Violations(Collections.emptyList());

	private final List<RuntimeException> exceptions;

	/**
	 * Creates a new {@link Violations} from the given {@link RuntimeException}s.
	 *
	 * @param exceptions must not be {@literal null}.
	 */
	private Violations(List<RuntimeException> exceptions) {

		Assert.notNull(exceptions, "Exceptions must not be null!");

		this.exceptions = exceptions;
	}

	/**
	 * A {@link Collector} to turn a {@link Stream} of {@link RuntimeException}s into a {@link Violations} instance.
	 *
	 * @return will never be {@literal null}.
	 */
	static Collector<RuntimeException, ?, Violations> toViolations() {
		return Collectors.collectingAndThen(Collectors.toList(), Violations::new);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	@Override
	public String getMessage() {

		return exceptions.stream() //
				.map(RuntimeException::getMessage) //
				.collect(Collectors.joining("\n- ", "- ", ""));
	}

	/**
	 * Returns all violations' messages.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	public List<String> getMessages() {

		return exceptions.stream() //
				.map(RuntimeException::getMessage)
				.toList();
	}

	/**
	 * Returns whether there are violations available.
	 *
	 * @return
	 */
	public boolean hasViolations() {
		return !exceptions.isEmpty();
	}

	/**
	 * Throws itself in case it's not an empty instance.
	 */
	public void throwIfPresent() {

		if (hasViolations()) {
			throw this;
		}
	}

	/**
	 * Creates a new {@link Violations} with the given {@link RuntimeException} added to the current ones?
	 *
	 * @param exception must not be {@literal null}.
	 * @return
	 */
	Violations and(RuntimeException exception) {

		Assert.notNull(exception, "Exception must not be null!");

		List<RuntimeException> newExceptions = new ArrayList<>(exceptions.size() + 1);
		newExceptions.addAll(exceptions);
		newExceptions.add(exception);

		return new Violations(newExceptions);
	}

	Violations and(Violations other) {

		List<RuntimeException> newExceptions = new ArrayList<>(exceptions.size() + other.exceptions.size());
		newExceptions.addAll(exceptions);
		newExceptions.addAll(other.exceptions);

		return new Violations(newExceptions);
	}

	Violations and(String violation) {
		return and(new ArchitecturalViolation(violation));
	}

	private static class ArchitecturalViolation extends RuntimeException {

		private static final long serialVersionUID = 3587887036508024142L;

		public ArchitecturalViolation(String message) {
			super(message);
		}
	}
}
