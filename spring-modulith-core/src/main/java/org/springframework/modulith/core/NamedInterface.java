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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.springframework.util.Assert;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasModifiers;

/**
 * A named interface into an {@link ApplicationModule}. This can either be a package, explicitly annotated with
 * {@link org.springframework.modulith.NamedInterface} or a set of types annotated with the same annotation. Other
 * {@link ApplicationModules} can define allowed dependencies to particular named interfaces via the
 * {@code $moduleName::$namedInterfaceName} syntax.
 *
 * @author Oliver Drotbohm
 * @see org.springframework.modulith.ApplicationModule#allowedDependencies()
 */
public abstract class NamedInterface implements Iterable<JavaClass> {

	private static final String UNNAMED_NAME = "<<UNNAMED>>";
	private static final String PACKAGE_INFO_NAME = "package-info";

	protected final String name;

	/**
	 * Creates a new {@link NamedInterface} with the given name.
	 *
	 * @param name must not be {@literal null} or empty.
	 */
	protected NamedInterface(String name) {

		Assert.hasText(name, "Name must not be null or empty!");

		this.name = name;
	}

	/**
	 * Returns all {@link PackageBasedNamedInterface}s for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static List<NamedInterface> of(JavaPackage javaPackage) {

		String[] name = javaPackage.getAnnotation(org.springframework.modulith.NamedInterface.class) //
				.map(it -> it.name()) //
				.orElseThrow(() -> new IllegalArgumentException(
						String.format("Couldn't find NamedInterface annotation on package %s!", javaPackage)));

		return Arrays.stream(name) //
				.<NamedInterface> map(it -> new PackageBasedNamedInterface(it, javaPackage)) //
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
	public static TypeBasedNamedInterface of(String name, Classes classes, JavaPackage basePackage) {
		return new TypeBasedNamedInterface(name, classes, basePackage);
	}

	/**
	 * Creates an unnamed {@link NamedInterface} for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterface unnamed(JavaPackage javaPackage) {
		return new PackageBasedNamedInterface(UNNAMED_NAME, javaPackage);
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

		return getClasses().contains(type);
	}

	/**
	 * Returns whether the {@link NamedInterface} contains the given type.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return !getClasses().that(Predicates.equivalentTo(type)).isEmpty();
	}

	/**
	 * Returns whether the given {@link NamedInterface} has the same name as the current one.
	 *
	 * @param other must not be {@literal null}.
	 */
	boolean hasSameNameAs(NamedInterface other) {

		Assert.notNull(other, "NamedInterface must not be null!");

		return this.name.equals(other.name);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<JavaClass> iterator() {
		return getClasses().iterator();
	}

	/**
	 * Returns all {@link Classes} making up this {@link NamedInterface}.
	 *
	 * @return will never be {@literal null}.
	 */
	protected abstract Classes getClasses();

	/**
	 * Merges the current {@link NamedInterface} with the given {@link TypeBasedNamedInterface}.
	 *
	 * @param other must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public abstract NamedInterface merge(TypeBasedNamedInterface other);

	private static class PackageBasedNamedInterface extends NamedInterface {

		private final Classes classes;
		private final JavaPackage javaPackage;

		public PackageBasedNamedInterface(String name, JavaPackage pkg) {

			super(name);

			Assert.notNull(pkg, "Package must not be null!");
			Assert.hasText(name, "Package name must not be null or empty!");

			this.classes = pkg.toSingle().getClasses() //
					.that(HasModifiers.Predicates.modifier(JavaModifier.PUBLIC)) //
					.that(DescribedPredicate.not(JavaClass.Predicates.simpleName(PACKAGE_INFO_NAME)));

			this.javaPackage = pkg;
		}

		private PackageBasedNamedInterface(String name, Classes classes, JavaPackage pkg) {

			super(name);
			this.classes = classes;
			this.javaPackage = pkg;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.NamedInterface#getClasses()
		 */
		@Override
		public Classes getClasses() {
			return classes;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.NamedInterface#merge(org.springframework.modulith.model.NamedInterface.TypeBasedNamedInterface)
		 */
		@Override
		public NamedInterface merge(TypeBasedNamedInterface other) {
			return new PackageBasedNamedInterface(name, classes.and(other.classes), javaPackage);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.NamedInterface#toString()
		 */
		@Override
		public String toString() {
			return String.format("%s - Public types residing in %s:\n%s\n", name, javaPackage.getName(),
					classes.format(javaPackage.getName()));
		}
	}

	public static class TypeBasedNamedInterface extends NamedInterface {

		private final Classes classes;
		private final JavaPackage pkg;

		/**
		 * Creates a new {@link TypeBasedNamedInterface} with the given name, {@link Classes} and {@link JavaPackage}.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @param types must not be {@literal null}.
		 * @param pkg must not be {@literal null}.
		 */
		public TypeBasedNamedInterface(String name, Classes types, JavaPackage pkg) {

			super(name);

			Assert.notNull(types, "Classes must not be null!");
			Assert.notNull(pkg, "JavaPackage must not be null!");

			this.classes = types;
			this.pkg = pkg;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.NamedInterface#getClasses()
		 */
		@Override
		public Classes getClasses() {
			return classes;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.NamedInterface#merge(org.springframework.modulith.model.NamedInterface.TypeBasedNamedInterface)
		 */
		@Override
		public NamedInterface merge(TypeBasedNamedInterface other) {
			return new TypeBasedNamedInterface(name, classes.and(other.classes), pkg);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.NamedInterface#toString()
		 */
		@Override
		public String toString() {
			return String.format("%s - Types underneath base package %s:\n%s\n", name, pkg.getName(),
					classes.format(pkg.getName()));
		}
	}
}
