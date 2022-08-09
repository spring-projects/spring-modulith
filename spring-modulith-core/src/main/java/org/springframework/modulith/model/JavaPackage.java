/*
 * Copyright 2018-2022 the original author or authors.
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
package org.springframework.modulith.model;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;

import com.tngtech.archunit.base.DescribedIterable;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * @author Oliver Drotbohm
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class JavaPackage implements DescribedIterable<JavaClass> {

	private static final String PACKAGE_INFO_NAME = "package-info";

	private final @Getter String name;
	private final Classes classes;
	private final Classes packageClasses;
	private final Supplier<Set<JavaPackage>> directSubPackages;

	private JavaPackage(Classes classes, String name, boolean includeSubPackages) {

		this.classes = classes;
		this.packageClasses = classes.that(resideInAPackage(includeSubPackages ? name.concat("..") : name));
		this.name = name;
		this.directSubPackages = Suppliers.memoize(() -> packageClasses.stream() //
				.map(it -> it.getPackageName()) //
				.filter(it -> !it.equals(name)) //
				.map(it -> extractDirectSubPackage(it)) //
				.distinct() //
				.map(it -> of(classes, it)) //
				.collect(Collectors.toSet()));
	}

	public static JavaPackage of(Classes classes, String name) {
		return new JavaPackage(classes, name, true);
	}

	public static boolean isPackageInfoType(JavaClass type) {
		return type.getSimpleName().equals(PACKAGE_INFO_NAME);
	}

	public JavaPackage toSingle() {
		return new JavaPackage(classes, name, false);
	}

	public String getLocalName() {
		return name.substring(name.lastIndexOf(".") + 1);
	}

	public Collection<JavaPackage> getDirectSubPackages() {
		return directSubPackages.get();
	}

	/**
	 * Returns all classes residing in the current package and potentially in sub-packages if the current package was
	 * created to include them.
	 *
	 * @return
	 */
	public Classes getClasses() {
		return packageClasses;
	}

	/**
	 * Extract the direct sub-package name of the given candidate.
	 *
	 * @param candidate
	 * @return
	 */
	private String extractDirectSubPackage(String candidate) {

		if (candidate.length() <= name.length()) {
			return candidate;
		}

		int subSubPackageIndex = candidate.indexOf('.', name.length() + 1);
		int endIndex = subSubPackageIndex == -1 ? candidate.length() : subSubPackageIndex;

		return candidate.substring(0, endIndex);
	}

	public Stream<JavaPackage> getSubPackagesAnnotatedWith(Class<? extends Annotation> annotation) {

		return packageClasses.that(JavaClass.Predicates.simpleName(PACKAGE_INFO_NAME) //
				.and(CanBeAnnotated.Predicates.annotatedWith(annotation))).stream() //
				.map(JavaClass::getPackageName) //
				.distinct() //
				.map(it -> of(classes, it));
	}

	public Classes that(DescribedPredicate<? super JavaClass> predicate) {
		return packageClasses.that(predicate);
	}

	public boolean contains(JavaClass type) {
		return packageClasses.contains(type);
	}

	public boolean contains(String className) {
		return packageClasses.contains(className);
	}

	public Stream<JavaClass> stream() {
		return packageClasses.stream();
	}

	public <A extends Annotation> Optional<A> getAnnotation(Class<A> annotationType) {

		return packageClasses.that(JavaClass.Predicates.simpleName(PACKAGE_INFO_NAME) //
				.and(CanBeAnnotated.Predicates.annotatedWith(annotationType))) //
				.toOptional() //
				.map(it -> it.reflect())
				.map(it -> AnnotatedElementUtils.getMergedAnnotation(it, annotationType));
	}

	/*
	 * (non-Javadoc)
	 * @see com.tngtech.archunit.base.HasDescription#getDescription()
	 */
	@Override
	public String getDescription() {
		return classes.getDescription();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<JavaClass> iterator() {
		return classes.iterator();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return new StringBuilder(name) //
				.append("\n") //
				.append(getClasses().format(name)) //
				.append('\n') //
				.toString();
	}
}
