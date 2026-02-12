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

import java.util.Objects;

import org.springframework.util.Assert;

/**
 * An individual architectural violation.
 *
 * @author Oliver Drotbohm
 * @since 1.3.1
 */
public class Violation {

	final String message;

	Violation(String message) {
		this.message = message;
	}

	/**
	 * Returns the violation message.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Returns whether the {@link Violation}'s message contains the given candidate.
	 *
	 * @param candidate must not be {@literal null} or empty.
	 */
	public boolean hasMessageContaining(String candidate) {

		Assert.hasText(candidate, "Candidate must not be null or empty!");

		return message.contains(candidate);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return message;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		return this == obj
				|| obj instanceof Violation that
						&& Objects.equals(this.message, that.message);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(message);
	}
}