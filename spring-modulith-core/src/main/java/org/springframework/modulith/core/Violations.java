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

	private final List<Violation> violations;

	/**
	 * Creates a new {@link Violations} from the given {@link ArchitecturalViolation}s.
	 *
	 * @param violations must not be {@literal null}.
	 */
	private Violations(List<Violation> violations) {

		Assert.notNull(violations, "Exceptions must not be null!");

		this.violations = violations;
	}

	/**
	 * A {@link Collector} to turn a {@link java.util.stream.Stream} of {@link RuntimeException}s into a
	 * {@link Violations} instance.
	 *
	 * @return will never be {@literal null}.
	 */
	static Collector<Violation, ?, Violations> toViolations() {
		return Collectors.collectingAndThen(Collectors.toList(), Violations::new);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	@Override
	public String getMessage() {

		return violations.stream() //
				.map(Violation::message) //
				.collect(Collectors.joining("\n- ", "- ", ""));
	}

	/**
	 * Returns all violations' messages.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.1
	 */
	public List<String> getMessages() {

		return violations.stream() //
				.map(Violation::message)
				.toList();
	}

	/**
	 * Returns whether there are violations available.
	 *
	 * @return
	 */
	public boolean hasViolations() {
		return !violations.isEmpty();
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
	 * @param violation must not be {@literal null}.
	 * @return
	 */
	Violations and(Violation violation) {

		Assert.notNull(violation, "Exception must not be null!");

		return new Violations(unionByMessage(violations, List.of(violation)));
	}

	Violations and(Violations other) {
		return new Violations(unionByMessage(violations, other.violations));
	}

	Violations and(String violation) {
		return and(new Violation(violation));
	}

	private List<Violation> unionByMessage(List<Violation> left, List<Violation> right) {

		if (left.isEmpty()) {
			return right;
		}

		if (right.isEmpty()) {
			return left;
		}

		var result = new ArrayList<>(left);

		var messages = left.stream().map(Violation::message).toList();

		right.stream()
				.filter(it -> !messages.contains(it.message()))
				.forEach(result::add);

		return result.size() == left.size() ? left : result;
	}

	static record Violation(String message) {}
}
