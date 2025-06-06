/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.modulith.events.core;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Identifier for a publication target.
 *
 * @author Oliver Drotbohm
 */
public class PublicationTargetIdentifier {

	private final String value;

	/**
	 * Creates a new {@link PublicationTargetIdentifier} for the given value.
	 *
	 * @param value must not be {@literal null}.
	 */
	private PublicationTargetIdentifier(String value) {

		Assert.hasText(value, "Value must not be null or empty!");

		this.value = value;
	}

	/**
	 * Returns the {@link PublicationTargetIdentifier} for the given value.
	 *
	 * @param value must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static PublicationTargetIdentifier of(String value) {
		return new PublicationTargetIdentifier(value);
	}

	/**
	 * Returns the raw String value of the identifier.
	 *
	 * @return the value
	 */
	public String getValue() {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof PublicationTargetIdentifier that)) {
			return false;
		}

		return Objects.equals(value, that.value);
	}

}
