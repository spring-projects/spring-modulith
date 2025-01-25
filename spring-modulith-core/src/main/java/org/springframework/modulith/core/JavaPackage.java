/*
 * Copyright 2018-2025 the original author or authors.
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

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.*;
import static com.tngtech.archunit.core.domain.properties.HasModifiers.Predicates.*;
import static java.util.stream.Collectors.*;
import static org.springframework.modulith.core.SyntacticSugar.*;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.PackageInfo;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;

import com.tngtech.archunit.base.DescribedIterable;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * An abstraction of a Java package.
 *
 * @author Oliver Drotbohm
 */
public class JavaPackage implements DescribedIterable<JavaClass>, Comparable<JavaPackage> {

	private static final String PACKAGE_INFO_NAME = "package-info";
	private static final String MULTIPLE_TYPES_ANNOTATED_WITH = "Expected maximum of one type in package %s to be annotated with %s, but got %s!";
	private static final DescribedPredicate<JavaClass> ARE_PACKAGE_INFOS = //
			has(simpleName(PACKAGE_INFO_NAME)).or(is(metaAnnotatedWith(PackageInfo.class)));

	private final PackageName name;
	private final Classes classes, packageClasses;
	private final Supplier<Set<JavaPackage>> directSubPackages;
	private final Supplier<JavaPackages> subPackages;

	/**
	 * Creates a new {@link JavaPackage} for the given {@link Classes}, name and whether to include all sub-packages.
	 *
	 * @param classes must not be {@literal null}.
	 * @param name must not be {@literal null}.
	 * @param includeSubPackages
	 */
	private JavaPackage(Classes classes, PackageName name, boolean includeSubPackages) {

		Assert.notNull(classes, "Classes must not be null!");

		this.classes = classes;
		this.packageClasses = classes
				.that(resideInAPackage(name.asFilter(includeSubPackages)));
		this.name = name;

		this.directSubPackages = () -> detectSubPackages()
				.filter(this::isDirectParentOf)
				.collect(Collectors.toUnmodifiableSet());

		this.subPackages = SingletonSupplier.of(() -> detectSubPackages()
				.collect(collectingAndThen(toUnmodifiableList(), JavaPackages::new)));
	}

	/**
	 * Creates a new {@link JavaPackage} for the given classes and name.
	 *
	 * @param classes must not be {@literal null}.
	 * @param name must not be {@literal null} or empty.
	 * @return
	 */
	public static JavaPackage of(Classes classes, String name) {
		return new JavaPackage(classes, PackageName.of(name), true);
	}

	/**
	 * Returns whether the given type is the {@code package-info.java} one.
	 *
	 * @param type must not be {@literal null}.
	 */
	public static boolean isPackageInfoType(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return ARE_PACKAGE_INFOS.test(type);
	}

	/**
	 * Returns the name of the package.
	 *
	 * @return will never be {@literal null}.
	 */
	public String getName() {
		return name.getName();
	}

	/**
	 * Returns the name of the given {@link JavaPackage} with regards to the current one.
	 *
	 * @param pkg must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	public String getTrailingName(JavaPackage pkg) {

		Assert.notNull(pkg, "JavaPackage must not be null!");
		Assert.isTrue(pkg.isSubPackageOf(this), "Given package must be a sub-package of the current one!");

		return pkg.getName().substring(getName().length() + 1);
	}

	/**
	 * Reduces the {@link JavaPackage} to only its base package.
	 *
	 * @return will never be {@literal null}.
	 */
	public JavaPackage toSingle() {
		return new JavaPackage(classes, name, false);
	}

	/**
	 * Returns the local name of the package, i.e. the last segment of the qualified package name.
	 *
	 * @return will never be {@literal null}.
	 */
	public String getLocalName() {
		return name.getLocalName();
	}

	/**
	 * Returns all direct sub-packages of the current one.
	 *
	 * @return will never be {@literal null}.
	 */
	public Collection<JavaPackage> getDirectSubPackages() {
		return directSubPackages.get();
	}

	/**
	 * Returns all classes residing in the current package and potentially in sub-packages if the current package was
	 * created to include them.
	 *
	 * @return will never be {@literal null}.
	 */
	public Classes getClasses() {
		return packageClasses;
	}

	/**
	 * Returns the classes exposed by this package, i.e. only public ones. Also filters the {@code package-info} type.
	 *
	 * @return will never be {@literal null}.
	 */
	public Classes getExposedClasses() {

		return packageClasses //
				.that(doNotHave(simpleName(PACKAGE_INFO_NAME))) //
				.that(have(modifier(JavaModifier.PUBLIC)));
	}

	/**
	 * Returns all sub-packages that carry the given annotation type.
	 *
	 * @param annotation must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public Stream<JavaPackage> getSubPackagesAnnotatedWith(Class<? extends Annotation> annotation) {

		Assert.notNull(annotation, "Annotation must not be null!");

		return packageClasses.that(ARE_PACKAGE_INFOS.and(are(metaAnnotatedWith(annotation)))).stream() //
				.map(JavaClass::getPackageName) //
				.filter(Predicate.not(name::hasName))
				.distinct() //
				.map(it -> of(classes, it));
	}

	/**
	 * Returns all {@link Classes} that match the given {@link DescribedPredicate}.
	 *
	 * @param predicate must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public Classes that(DescribedPredicate<? super JavaClass> predicate) {

		Assert.notNull(predicate, "Predicate must not be null!");

		return packageClasses.that(predicate);
	}

	/**
	 * Return whether the {@link JavaPackage} contains the given type.
	 *
	 * @param type must not be {@literal null}.
	 */
	public boolean contains(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return packageClasses.contains(type);
	}

	/**
	 * Returns whether the {@link JavaPackage} contains the type with the given name.
	 *
	 * @param typeName must not be {@literal null} or empty.
	 */
	public boolean contains(String typeName) {

		Assert.hasText(typeName, "Type name must not be null or empty!");

		return packageClasses.contains(typeName);
	}

	/**
	 * Returns a {@link Stream} of all {@link JavaClass}es contained in the {@link JavaPackage}.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<JavaClass> stream() {
		return packageClasses.stream();
	}

	/**
	 * Return the annotation of the given type declared on the package.
	 *
	 * @param <A> the annotation type.
	 * @param annotationType the annotation type to be found.
	 * @return will never be {@literal null}.
	 */
	public <A extends Annotation> Optional<A> getAnnotation(Class<A> annotationType) {

		return packageClasses.that(have(simpleName(PACKAGE_INFO_NAME)) //
				.and(are(metaAnnotatedWith(annotationType)))) //
				.toOptional() //
				.map(it -> it.reflect())
				.map(it -> AnnotatedElementUtils.getMergedAnnotation(it, annotationType));
	}

	/**
	 * Returns the name of the package.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	PackageName getPackageName() {
		return name;
	}

	/**
	 * Returns a filter expression including all types within that package and any nested package.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	String asFilter() {
		return name.asFilter(true);
	}

	/**
	 * Returns a new {@link Stream} of the current package and all its sub-packages annotated with the given annotation
	 * type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Stream<JavaPackage> andSubPackagesAnnotatedWith(Class<? extends Annotation> type) {

		Assert.notNull(type, "Annotation type must not be null!");

		return Stream.concat(Stream.of(this), getSubPackagesAnnotatedWith(type));
	}

	/**
	 * Returns whether the given reference package is a sub-package of the current one.
	 *
	 * @param reference must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	boolean isSubPackageOf(JavaPackage reference) {

		Assert.notNull(reference, "Reference package must not be null!");

		return name.isSubPackageOf(reference.name);
	}

	/**
	 * Returns all Classes residing in the current package but not in any of the given sub-packages.
	 *
	 * @param exclusions must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Classes getClasses(Iterable<JavaPackage> exclusions) {

		Assert.notNull(exclusions, "Object must not be null!");

		var excludedPackages = StreamSupport.stream(exclusions.spliterator(), false)
				.map(JavaPackage::asFilter)
				.toArray(String[]::new);

		return packageClasses.that(resideOutsideOfPackages(excludedPackages));
	}

	/**
	 * Returns a {@link JavaPackages} instance representing all sub-packages.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	JavaPackages getSubPackages() {
		return subPackages.get();
	}

	/**
	 * Returns the sub-package with the given local name.
	 *
	 * @param localName must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Optional<JavaPackage> getSubPackage(String localName) {

		Assert.hasText(localName, "Local name must not be null or empty!");

		return getSubPackages().stream()
				.filter(it -> it.getLocalName().equals(localName))
				.findFirst();
	}

	/**
	 * Finds the annotation of the given type declared on the package itself or any type located the direct package's
	 * types .
	 *
	 * @param <A> the type of the annotation.
	 * @param annotationType must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.2
	 * @throws IllegalStateException in case multiple types in the current package are annotated with the given
	 *           annotation.
	 */
	public <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationType) {

		return getAnnotation(annotationType)
				.or(() -> {

					var annotatedTypes = toSingle().packageClasses
							.that(are(metaAnnotatedWith(PackageInfo.class).and(are(metaAnnotatedWith(annotationType)))))
							.stream()
							.map(it -> it.getAnnotationOfType(annotationType))
							.toList();

					if (annotatedTypes.size() > 1) {

						throw new IllegalStateException(MULTIPLE_TYPES_ANNOTATED_WITH.formatted(name,
								FormattableType.of(annotationType).getAbbreviatedFullName(), annotatedTypes));
					}

					return annotatedTypes.isEmpty() ? Optional.empty() : Optional.of(annotatedTypes.get(0));
				});
	}

	/**
	 * Returns whether the current {@link JavaPackage} is the direct parent of the given one.
	 *
	 * @param reference must not be {@literal null}.
	 * @since 1.4
	 */
	boolean isDirectParentOf(JavaPackage reference) {

		Assert.notNull(reference, "Reference JavaPackage must not be null!");

		var name = reference.getPackageName();

		return name.hasParent() && this.getPackageName().equals(name.getParent());
	}

	/*
	 * (non-Javadoc)
	 * @see com.tngtech.archunit.base.HasDescription#getDescription()
	 */
	@Override
	public String getDescription() {
		return classes.getDescription();
	}

	private Stream<JavaPackage> detectSubPackages() {

		return packageClasses.stream() //
				.map(JavaClass::getPackageName)
				.filter(Predicate.not(name::hasName))
				.map(PackageName::of)
				.flatMap(name::expandUntil)
				.distinct()
				.map(it -> new JavaPackage(classes, it, true));
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
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(JavaPackage o) {
		return name.compareTo(o.name);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return new StringBuilder(name.toString()) //
				.append("\n") //
				.append(getClasses().format(name.toString())) //
				.append('\n') //
				.toString();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof JavaPackage that)) {
			return false;
		}

		return Objects.equals(this.classes, that.classes) //
				&& Objects.equals(this.getDirectSubPackages(), that.getDirectSubPackages()) //
				&& Objects.equals(this.name, that.name) //
				&& Objects.equals(this.packageClasses, that.packageClasses);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(classes, directSubPackages.get(), name, packageClasses);
	}

	static Comparator<JavaPackage> reverse() {
		return (left, right) -> -left.compareTo(right);
	}
}
