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

import java.util.Objects;

import org.springframework.util.Assert;

/**
 * An identifier of an {@link ApplicationModule}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
public class ApplicationModuleIdentifier implements Comparable<ApplicationModuleIdentifier> {

	private final String identifier;

	/**
	 * Creates a new {@link ApplicationModuleIdentifier} for the given {@link String}.
	 *
	 * @param identifier must not be {@literal null} .
	 */
	private ApplicationModuleIdentifier(String identifier) {

		Assert.hasText(identifier, "Identitifier must not be null or empty!");
		Assert.isTrue(!identifier.contains("::"), "Identifier must not contain a double-colon!");

		this.identifier = identifier;
	}

	/**
	 * Returns the {@link ApplicationModuleIdentifier} for the given source {@link String}.
	 *
	 * @param identifier must not be {@literal null}, empty or contain a double colon ({@code ::}).
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModuleIdentifier of(String identifier) {
		return new ApplicationModuleIdentifier(identifier);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(ApplicationModuleIdentifier o) {
		return identifier.compareTo(o.identifier);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return identifier;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (obj == this) {
			return true;
		}

		if (!(obj instanceof ApplicationModuleIdentifier that)) {
			return false;
		}

		return Objects.equals(this.identifier, that.identifier);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(identifier);
	}
}
