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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.base.DescribedIterable;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasName;

/**
 * @author Oliver Drotbohm
 */
class Classes implements DescribedIterable<JavaClass> {

	public static Classes NONE = Classes.of(Collections.emptyList());

	private final List<JavaClass> classes;

	/**
	 * Creates a new {@link Classes} for the given {@link JavaClass}es.
	 *
	 * @param classes must not be {@literal null}.
	 */
	private Classes(List<JavaClass> classes) {

		Assert.notNull(classes, "JavaClasses must not be null!");

		this.classes = classes.stream() //
				.sorted(Comparator.comparing(JavaClass::getName)) //
				.toList();
	}

	/**
	 * Creates a new {@link Classes} for the given {@link JavaClass}es.
	 *
	 * @param classes must not be {@literal null}.
	 * @return
	 */
	static Classes of(JavaClasses classes) {

		return new Classes(StreamSupport.stream(classes.spliterator(), false) //
				.toList());
	}

	/**
	 * Creates a new {@link Classes} for the given {@link JavaClass}es.
	 *
	 * @param classes must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static Classes of(List<JavaClass> classes) {
		return new Classes(classes);
	}

	/**
	 * Returns a {@link Collector} creating a {@link Classes} instance from a {@link Stream} of
	 * {@link com.tngtech.archunit.core.domain.JavaType}.
	 *
	 * @return will never be {@literal null}.
	 */
	static Collector<JavaClass, ?, Classes> toClasses() {
		return Collectors.collectingAndThen(Collectors.toList(), Classes::of);
	}

	/**
	 * Returns {@link Classes} that match the given {@link DescribedPredicate}.
	 *
	 * @param predicate must not be {@literal null}.
	 * @return
	 */
	Classes that(DescribedPredicate<? super JavaClass> predicate) {

		Assert.notNull(predicate, "Predicate must not be null!");

		return classes.stream() //
				.filter((Predicate<JavaClass>) it -> predicate.test(it)) //
				.collect(Collectors.collectingAndThen(Collectors.toList(), Classes::new));
	}

	/**
	 * Returns all classes that reside the given {@link PackageName}.
	 *
	 * @param name must not be {@literal null}.
	 * @param nested whether to include nested packages
	 * @return will never be {@literal null}.
	 */
	Classes thatResideIn(PackageName name, boolean nested) {

		var result = new ArrayList<JavaClass>();

		for (JavaClass candidate : classes) {
			if (residesIn(name, candidate, nested)) {
				result.add(candidate);
			}
		}

		return new Classes(result);
	}

	Classes and(Classes classes) {
		return and(classes.classes);
	}

	/**
	 * Returns a Classes with the current elements and the given other ones combined.
	 *
	 * @param others must not be {@literal null}.
	 * @return
	 */
	Classes and(Collection<JavaClass> others) {

		Assert.notNull(others, "JavaClasses must not be null!");

		if (others.isEmpty()) {
			return this;
		}

		List<JavaClass> result = new ArrayList<>(classes);

		others.forEach(it -> {
			if (!result.contains(it)) {
				result.add(it);
			}
		});

		return new Classes(result);
	}

	public Stream<JavaClass> stream() {
		return classes.stream();
	}

	boolean isEmpty() {
		return !classes.iterator().hasNext();
	}

	Optional<JavaClass> toOptional() {
		return isEmpty() ? Optional.empty() : Optional.of(classes.iterator().next());
	}

	boolean contains(JavaClass type) {
		return !that(new SameClass(type)).isEmpty();
	}

	boolean contains(String className) {
		return !that(HasName.Predicates.name(className)).isEmpty();
	}

	JavaClass getRequiredClass(Class<?> type) {

		return classes.stream() //
				.filter(it -> it.isEquivalentTo(type)) //
				.findFirst() //
				.orElseThrow(
						() -> new IllegalArgumentException(String.format("No JavaClass found for type %s!", type)));
	}

	/*
	 * (non-Javadoc)
	 * @see com.tngtech.archunit.base.HasDescription#getDescription()
	 */
	@Override
	public String getDescription() {
		return "";
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
		return "Classes [classes=" + classes + "]";
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

		if (!(obj instanceof Classes that)) {
			return false;
		}

		return Objects.equals(classes, that.classes);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(classes);
	}

	String format() {

		return classes.stream() //
				.map(Classes::format) //
				.collect(Collectors.joining("\n"));
	}

	String format(String basePackage) {

		return classes.stream() //
				.map(it -> Classes.format(it, basePackage)) //
				.collect(Collectors.joining("\n"));
	}

	static String format(JavaClass type, String basePackage) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(basePackage, "Base package must not be null!");

		return format(type, basePackage, type.getModifiers().contains(JavaModifier.PUBLIC));
	}

	/**
	 * Formats the given {@link JavaClass} into a {@link String}, potentially abbreviating the given base package.
	 *
	 * @param type must not be {@literal null}.
	 * @param basePackage must not be {@literal null}.
	 * @param exposed whether the given type is considered exposed or not.
	 * @return will never be {@literal null}.
	 */
	static String format(JavaClass type, String basePackage, boolean exposed) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(basePackage, "Base package must not be null!");

		var name = StringUtils.hasText(basePackage) //
				? type.getName().replace(basePackage, "…") //
				: type.getName();

		return String.format("%s %s", exposed ? "+" : "o", name);
	}

	private static String format(JavaClass type) {
		return format(type, "");
	}

	private static boolean residesIn(PackageName reference, JavaClass type, boolean inNested) {

		var typesPackage = PackageName.ofType(type.getFullName());

		return inNested ? reference.contains(typesPackage) : reference.equals(typesPackage);
	}

	private static class SameClass extends DescribedPredicate<JavaClass> {

		private final JavaClass reference;

		public SameClass(JavaClass reference) {
			super(" is the same class as ");
			this.reference = reference;
		}

		/*
		 * (non-Javadoc)
		 * @see java.util.function.Predicate#test(java.lang.Object)
		 */
		@Override
		public boolean test(@Nullable JavaClass input) {
			return input != null && reference.getName().equals(input.getName());
		}
	}
}
