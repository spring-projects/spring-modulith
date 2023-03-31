/*
 * Copyright 2018-2023 the original author or authors.
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

import java.util.Iterator;
import java.util.List;

import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;

/**
 * A named interface into an {@link ApplicationModule}. This can either be a package, explicitly annotated with
 * {@link org.springframework.modulith.NamedInterface} or a set of types annotated with the same annotation. Other
 * {@link ApplicationModules} can define allowed dependencies to particular named interfaces via the
 * {@code $moduleName::$namedInterfaceName} syntax.
 *
 * @author Oliver Drotbohm
 * @see org.springframework.modulith.ApplicationModule#allowedDependencies()
 */
public class NamedInterface implements Iterable<JavaClass> {

	static final String UNNAMED_NAME = "<<UNNAMED>>";

	private final String name;
	private final Classes classes;

	/**
	 * Creates a new {@link NamedInterface} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 */
	private NamedInterface(String name, Classes classes) {

		Assert.hasText(name, "Name must not be null or empty!");

		this.name = name;
		this.classes = classes;
	}

	/**
	 * Returns all {@link PackageBasedNamedInterface}s for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static List<NamedInterface> of(JavaPackage javaPackage) {

		var names = javaPackage.getAnnotation(org.springframework.modulith.NamedInterface.class) //
				.map(it -> getDefaultedNames(it, javaPackage.getName())) //
				.orElseThrow(() -> new IllegalArgumentException(
						String.format("Couldn't find NamedInterface annotation on package %s!", javaPackage)));

		var classes = javaPackage.toSingle().getExposedClasses();

		return names.stream()
				.<NamedInterface> map(it -> new NamedInterface(it, classes)) //
				.toList();
	}

	/**
	 * Returns a {@link TypeBasedNamedInterface} with the given name, {@link Classes} and base {@link JavaPackage}.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @param classes must not be {@literal null}.
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterface of(String name, Classes classes) {
		return new NamedInterface(name, classes);
	}

	/**
	 * Creates an unnamed {@link NamedInterface} for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterface unnamed(JavaPackage javaPackage) {
		return new NamedInterface(UNNAMED_NAME, javaPackage.toSingle().getExposedClasses());
	}

	/**
	 * Returns the {@link NamedInterface}'s name.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns whether this is the unnamed (implicit) {@link NamedInterface}.
	 */
	public boolean isUnnamed() {
		return name.equals(UNNAMED_NAME);
	}

	/**
	 * Returns whether the {@link NamedInterface} contains the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(JavaClass type) {

		Assert.notNull(type, "JavaClass must not be null!");

		return classes.contains(type);
	}

	/**
	 * Returns whether the {@link NamedInterface} contains the given type.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return !classes.that(Predicates.equivalentTo(type)).isEmpty();
	}

	/**
	 * Returns whether the given {@link NamedInterface} has the same name as the current one.
	 *
	 * @param other must not be {@literal null}.
	 */
	boolean hasSameNameAs(NamedInterface other) {

		Assert.notNull(other, "NamedInterface must not be null!");

		return name.equals(other.name);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<JavaClass> iterator() {
		return classes.iterator();
	}

	/**
	 * Merges the current {@link NamedInterface} with the given {@link TypeBasedNamedInterface}.
	 *
	 * @param other must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	NamedInterface merge(NamedInterface other) {

		Assert.isTrue(this.name.equals(other.name),
				() -> "Named interfaces name must be equal to %s but was %s".formatted(name, other.name));

		return new NamedInterface(name, classes.and(other.classes));
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "NamedInterface: name=%s, types=%s".formatted(name, classes);
	}

	/**
	 * Returns the names declared in the given {@link org.springframework.modulith.NamedInterface} annotation or defaults
	 * to the local name of the given package if none declared.
	 *
	 * @param annotation must not be {@literal null}.
	 * @param packageName must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static List<String> getDefaultedNames(org.springframework.modulith.NamedInterface annotation, String packageName) {

		Assert.notNull(annotation, "NamedInterface must not be null!");
		Assert.hasText(packageName, "Package name must not be null or empty!");

		var declaredNames = annotation.name();

		return declaredNames.length == 0
				? List.of(packageName.substring(packageName.lastIndexOf('.') + 1))
				: List.of(declaredNames);
	}
}
