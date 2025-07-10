/*
 * Copyright 2023-2025 the original author or authors.
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * A collection of {@link JavaPackage}s.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class JavaPackages implements Iterable<JavaPackage> {

	public static JavaPackages NONE = new JavaPackages(Collections.emptyList());

	private final List<JavaPackage> packages;

	/**
	 * Creates a new {@link JavaPackages} instance for the given {@link JavaPackage}s.
	 *
	 * @param packages must not be {@literal null}.
	 */
	JavaPackages(Collection<JavaPackage> packages) {

		Assert.notNull(packages, "Packages must not be null!");

		this.packages = packages.stream().sorted().toList();
	}

	/**
	 * Creates a new {@link JavaPackages} instance for the given {@link JavaPackage}s.
	 *
	 * @param packages must not be {@literal null}.
	 */
	JavaPackages(JavaPackage... packages) {
		this(List.of(packages));
	}

	/**
	 * Returns a {@link JavaPackages} instance that only contains the primary packages contained in the current
	 * {@link JavaPackages}. Any package that's a sub-package of any other package will get dropped.
	 * <p>
	 * In other words for a list of {code com.foo}, {@code com.bar}, and {@code com.foo.bar}, only {@code com.foo} and
	 * {@code com.bar} will be retained.
	 *
	 * @return will never be {@literal null}.
	 */
	JavaPackages flatten() {
		return packages.isEmpty() ? this : new JavaPackages(removeSubPackages(packages));
	}

	/**
	 * Returns a stream of {@link JavaPackage}s.
	 *
	 * @return will never be {@literal null}.
	 */
	Stream<JavaPackage> stream() {
		return packages.stream();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<JavaPackage> iterator() {
		return packages.iterator();
	}

	/**
	 * Removes all sub-packages from the given list of packages.
	 *
	 * @param packages must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private static List<JavaPackage> removeSubPackages(List<JavaPackage> packages) {

		Assert.notNull(packages, "Packages must not be null!");

		if (packages.isEmpty()) {
			return Collections.emptyList();
		}

		var result = new ArrayList<JavaPackage>();

		for (JavaPackage candidate : packages) {
			if (result.stream().noneMatch(candidate::isSubPackageOf)) {
				result.add(candidate);
			}
		}

		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return packages.toString();
	}
}
