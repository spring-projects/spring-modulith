/*
 * Copyright 2023 the original author or authors.
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

import org.springframework.util.Assert;

/**
 * The name of a Java package. Packages are sortable comparing their individual segments and deeper packages sorted
 * last.
 *
 * @author Oliver Drotbohm
 * @since 1.2
 */
class PackageName implements Comparable<PackageName> {

	private final String name;
	private final String[] segments;

	/**
	 * Creates a new {@link PackageName} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 */
	public PackageName(String name) {

		Assert.hasText(name, "Name must not be null or empty!");

		this.name = name;
		this.segments = name.split("\\.");
	}

	/**
	 * Returns the length of the package name.
	 *
	 * @return will never be {@literal null}.
	 */
	int length() {
		return name.length();
	}

	/**
	 * Returns the raw name.
	 *
	 * @return will never be {@literal null}.
	 */
	String getName() {
		return name;
	}

	/**
	 * Returns whether the {@link PackageName} has the given {@link String} name.
	 *
	 * @param name must not be {@literal null} or empty.
	 */
	boolean hasName(String name) {

		Assert.hasText(name, "Name must not be null or empty!");

		return this.name.equals(name);
	}

	/**
	 * Returns the last segment of a package name.
	 *
	 * @return will never be {@literal null}.
	 */
	String getLocalName() {
		return segments[segments.length - 1];
	}

	/**
	 * Returns the nested name in reference to the given base.
	 *
	 * @param base must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	String getLocalName(String base) {

		Assert.hasText(base, "Base must not be null or empty!");
		Assert.isTrue(name.startsWith(base + "."),
				() -> "Given name %s is not a parent of the current package %s!".formatted(base, name));

		return name.substring(base.length() + 1);
	}

	/**
	 * Returns the filter expression to include all types including from nested packages.
	 *
	 * @return will never be {@literal null}.
	 */
	String asFilter(boolean includeNested) {
		return includeNested ? name.concat("..") : name;
	}

	/**
	 * Returns whether the current {@link PackageName} is the name of a parent package of the given one.
	 *
	 * @param reference must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	boolean isParentPackageOf(PackageName reference) {

		Assert.notNull(reference, "Reference package name must not be null!");

		return reference.name.startsWith(name + ".");
	}

	/**
	 * Returns whether the current {@link PackageName} is the name of a sub-package with the given name.
	 *
	 * @param reference must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	boolean isSubPackageOf(PackageName reference) {

		Assert.notNull(reference, "Reference package name must not be null!");

		return name.startsWith(reference.name + ".");
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(PackageName o) {

		for (var i = 0; i < segments.length; i++) {

			if (o.segments.length <= i) {
				return 1;
			}

			var segCompare = segments[i].compareTo(o.segments[i]);

			if (segCompare != 0) {
				return segCompare;
			}
		}

		return segments.length - o.segments.length;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return name;
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

		if (!(obj instanceof PackageName that)) {
			return false;
		}

		return this.name.equals(that.name);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
