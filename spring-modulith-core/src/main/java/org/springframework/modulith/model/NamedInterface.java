/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasModifiers;

/**
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class NamedInterface implements Iterable<JavaClass> {

	private static final String UNNAMED_NAME = "<<UNNAMED>>";
	private static final String PACKAGE_INFO_NAME = "package-info";

	protected final @Getter String name;

	static NamedInterface unnamed(JavaPackage javaPackage) {
		return new PackageBasedNamedInterface(UNNAMED_NAME, javaPackage);
	}

	public static List<PackageBasedNamedInterface> of(JavaPackage javaPackage) {

		String[] name = javaPackage.getAnnotation(org.springframework.modulith.NamedInterface.class) //
				.map(it -> it.value()) //
				.orElseThrow(() -> new IllegalArgumentException(
						String.format("Couldn't find NamedInterface annotation on package %s!", javaPackage)));

		return Arrays.stream(name) //
				.map(it -> new PackageBasedNamedInterface(it, javaPackage)) //
				.collect(Collectors.toList());
	}

	public static TypeBasedNamedInterface of(String name, Classes classes, JavaPackage basePackage) {
		return new TypeBasedNamedInterface(name, classes, basePackage);
	}

	public boolean isUnnamed() {
		return name.equals(UNNAMED_NAME);
	}

	public boolean contains(JavaClass type) {
		return getClasses().contains(type);
	}

	public boolean contains(Class<?> type) {
		return !getClasses().that(Predicates.equivalentTo(type)).isEmpty();
	}

	/**
	 * Returns whether the given {@link NamedInterface} has the same name as the current one.
	 *
	 * @param other
	 * @return
	 */
	boolean hasSameNameAs(NamedInterface other) {
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

	protected abstract Classes getClasses();

	public abstract NamedInterface merge(TypeBasedNamedInterface other);

	static class PackageBasedNamedInterface extends NamedInterface {

		private final @Getter Classes classes;
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

	static class TypeBasedNamedInterface extends NamedInterface {

		private final @Getter Classes classes;
		private final JavaPackage pkg;

		public TypeBasedNamedInterface(String name, Classes types, JavaPackage pkg) {
			super(name);

			this.classes = types;
			this.pkg = pkg;
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
