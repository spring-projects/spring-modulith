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

import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.core.Types.JavaTypes;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * A collection of {@link NamedInterface}s.
 *
 * @author Oliver Drotbohm
 */
public class NamedInterfaces implements Iterable<NamedInterface> {

	public static final NamedInterfaces NONE = new NamedInterfaces(Collections.emptyList());

	private final List<NamedInterface> namedInterfaces;

	/**
	 * Creates a new {@link NamedInterfaces} for all {@link NamedInterface}s.
	 *
	 * @param namedInterfaces must not be {@literal null}.
	 */
	private NamedInterfaces(List<NamedInterface> namedInterfaces) {

		Assert.notNull(namedInterfaces, "Named interfaces must not be null!");

		this.namedInterfaces = namedInterfaces.stream()
				.sorted(Comparator.comparing(NamedInterface::getName))
				.toList();
	}

	/**
	 * @param basePackage must not be {@literal null}.
	 * @param information must not be {@literal null}.
	 * @return
	 * @since 1.4
	 */
	public static NamedInterfaces of(JavaPackage basePackage, ApplicationModuleInformation information) {

		return information.isOpen()
				? NamedInterfaces.forOpen(basePackage)
				: NamedInterfaces.discoverNamedInterfaces(basePackage);
	}

	/**
	 * Discovers all {@link NamedInterfaces} declared for the given {@link JavaPackage}.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.4 (previously package protected)
	 */
	public static NamedInterfaces discoverNamedInterfaces(JavaPackage basePackage) {

		return NamedInterfaces.of(NamedInterface.unnamed(basePackage))
				.and(ofAnnotatedPackages(basePackage))
				.and(ofAnnotatedTypes(basePackage.getClasses()));
	}

	/**
	 * Creates a new {@link Builder} to eventually create {@link NamedInterfaces} for the given base package.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Builder builder(JavaPackage basePackage) {
		return new Builder(basePackage);
	}

	/**
	 * Creates a new {@link NamedInterfaces} for the given {@link NamedInterface}s.
	 *
	 * @param interfaces must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterfaces of(List<NamedInterface> interfaces) {
		return interfaces.isEmpty() ? NONE : new NamedInterfaces(interfaces);
	}

	/**
	 * Creates a new {@link NamedInterfaces} for the given base {@link JavaPackage}.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static NamedInterfaces ofAnnotatedPackages(JavaPackage basePackage) {

		Assert.notNull(basePackage, "Base package must not be null!");

		return basePackage //
				.getSubPackagesAnnotatedWith(org.springframework.modulith.NamedInterface.class) //
				.flatMap(it -> NamedInterface.of(it).stream()) //
				.collect(Collectors.collectingAndThen(Collectors.toList(), NamedInterfaces::of));
	}

	/**
	 * Creates a new {@link NamedInterface} consisting of the unnamed one containing all classes in the given
	 * {@link JavaPackage}.
	 *
	 * @param basePackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.2, public since 1.4
	 */
	public static NamedInterfaces forOpen(JavaPackage basePackage) {

		return NamedInterfaces.of(NamedInterface.open(basePackage))
				.and(ofAnnotatedPackages(basePackage))
				.and(ofAnnotatedTypes(basePackage.getClasses()));
	}

	/**
	 * Returns whether at least one explicit {@link NamedInterface} is declared.
	 *
	 * @return will never be {@literal null}.
	 */
	public boolean hasExplicitInterfaces() {
		return namedInterfaces.size() > 1 || !namedInterfaces.get(0).isUnnamed();
	}

	/**
	 * Create a {@link Stream} of {@link NamedInterface}s.
	 *
	 * @return will never be {@literal null}.
	 */
	public Stream<NamedInterface> stream() {
		return namedInterfaces.stream();
	}

	/**
	 * Returns the {@link NamedInterface} with the given name if present.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public Optional<NamedInterface> getByName(String name) {

		Assert.hasText(name, "Named interface name must not be null or empty!");

		return namedInterfaces.stream().filter(it -> it.getName().equals(name)).findFirst();
	}

	/**
	 * Returns the unnamed {@link NamedInterface} of the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public NamedInterface getUnnamedInterface() {

		return namedInterfaces.stream() //
				.filter(NamedInterface::isUnnamed) //
				.findFirst() //
				.orElseThrow(() -> new IllegalStateException("No unnamed interface found!"));
	}

	/**
	 * Returns all named interfaces that contain the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public Stream<NamedInterface> getNamedInterfacesContaining(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return namedInterfaces.stream()
				.filter(it -> it.contains(type));
	}

	/**
	 * Returns whether the given type is contained in one of the explicitly named {@link NamedInterface}s.
	 *
	 * @param type must not be {@literal null}.
	 * @since 1.2
	 */
	public boolean containsInExplicitInterface(JavaClass type) {

		Assert.notNull(type, "Type must not be null!");

		return getNamedInterfacesContaining(type)
				.anyMatch(NamedInterface::isNamed);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<NamedInterface> iterator() {
		return namedInterfaces.iterator();
	}

	/**
	 * Creates a new {@link NamedInterfaces} instance with the given {@link NamedInterface}s added.
	 *
	 * @param others must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.4 (previously package protected)
	 */
	public NamedInterfaces and(Iterable<NamedInterface> others) {

		Assert.notNull(others, "Other NamedInterfaces must not be null!");

		var namedInterfaces = new ArrayList<NamedInterface>();
		var unmergedInterfaces = new ArrayList<>(this.namedInterfaces);

		if (!others.iterator().hasNext()) {
			return this;
		}

		for (NamedInterface candidate : others) {

			var existing = this.namedInterfaces.stream() //
					.filter(candidate::hasSameNameAs) //
					.findFirst();

			// Merge existing with new and add to result
			existing.ifPresentOrElse(it -> {
				namedInterfaces.add(it.merge(candidate));
				unmergedInterfaces.remove(it);
			},
					() -> namedInterfaces.add(candidate));
		}

		namedInterfaces.addAll(unmergedInterfaces);

		return new NamedInterfaces(namedInterfaces);
	}

	Stream<NamedInterface> getNamedInterfacesContaining(Class<?> type) {

		return namedInterfaces.stream()
				.filter(it -> it.contains(type));
	}

	boolean containsInExplicitInterface(Class<?> type) {

		return getNamedInterfacesContaining(type)
				.anyMatch(NamedInterface::isNamed);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return namedInterfaces.stream()
				.map(NamedInterface::toString)
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private static NamedInterfaces of(NamedInterface interfaces) {
		return new NamedInterfaces(List.of(interfaces));
	}

	private static List<NamedInterface> ofAnnotatedTypes(Classes classes) {

		var mappings = new LinkedMultiValueMap<String, JavaClass>();

		classes.stream() //
				.filter(it -> !JavaPackage.isPackageInfoType(it)) //
				.forEach(it -> {

					if (!it.isMetaAnnotatedWith(org.springframework.modulith.NamedInterface.class)) {
						return;
					}

					var annotation = AnnotatedElementUtils.getMergedAnnotation(it.reflect(),
							org.springframework.modulith.NamedInterface.class);

					if (annotation == null) {
						throw new IllegalStateException("No @NamedInterface annotation found!");
					}

					NamedInterface.getDefaultedNames(annotation, it.getPackageName()).forEach(name -> {

						if (annotation.propagate()) {
							mappings.addAll(name, JavaTypes.relatedTypesOf(it, classes::contains).toList());
						} else {
							mappings.add(name, it);
						}
					});
				});

		return mappings.entrySet().stream() //
				.map(entry -> NamedInterface.of(entry.getKey(), Classes.of(entry.getValue()))) //
				.toList();
	}

	/**
	 * A builder API to manually construct {@link NamedInterfaces} instances. Allows selecting packages to create
	 * {@link NamedInterface} instances for based on excluding and including predicates, name matches etc. Will always
	 * include the unnamed named interface as it's required for a valid application module.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.4
	 */
	public static class Builder {

		private final JavaPackage basePackage;
		private final boolean recursive;
		private final Predicate<JavaPackage> inclusions, exclusions;

		/**
		 * Creates a new {@link Builder} for the given {@link JavaPackage}.
		 *
		 * @param basePackage must not be {@literal null}.
		 */
		private Builder(JavaPackage basePackage) {
			this(basePackage, false, __ -> false, __ -> true);
		}

		private Builder(JavaPackage basePackage, boolean recursive,
				Predicate<JavaPackage> inclusions, Predicate<JavaPackage> exclusions) {

			Assert.notNull(basePackage, "Base package must not be null!");
			Assert.notNull(inclusions, "Inclusions must not be null!");
			Assert.notNull(exclusions, "Exclusions must not be null!");

			this.basePackage = basePackage;
			this.recursive = recursive;
			this.inclusions = inclusions;
			this.exclusions = exclusions;
		}

		/**
		 * Configures the builder to not only consider directly nested packages but also ones nested in those.
		 *
		 * @return will never be {@literal null}.
		 */
		public Builder recursive() {
			return new Builder(basePackage, true, inclusions, exclusions);
		}

		/**
		 * Adds all packages with the trailing name relative to the base package matching the given names or expressions,
		 * unless set up to be excluded (see {@link #excluding(Predicate)} and overloads).. For a base package
		 * {@code com.acme}, the trailing name of package {@code com.acme.foo} would be {@code foo}. For
		 * {@code com.acme.foo.bar} it would be {@code foo.bar}.
		 * <p>
		 * Expressions can use wildcards, such as {@literal *} (for multi-character matches) and {@literal ?} (for
		 * single-character ones). As soon as an expression contains a dot ({@literal .}), the expression is applied to the
		 * entire trailing name. Expressions with a dot are applied to all name segments individually. In other words, an
		 * expression {@code foo} would match both the trailing names {@code foo}, {@code foo.bar}, and {@code bar.foo}.
		 *
		 * @param names must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #excluding(String...)
		 */
		public Builder matching(String... names) {
			return matching(List.of(names));
		}

		/**
		 * Adds all packages with the trailing name relative to the base package matching the given names or expressions,
		 * unless set up to be excluded (see {@link #excluding(Predicate)} and overloads). For a base package
		 * {@code com.acme}, the trailing name of package {@code com.acme.foo} would be {@code foo}. For
		 * {@code com.acme.foo.bar} it would be {@code foo.bar}.
		 * <p>
		 * Expressions can use wildcards, such as {@literal *} (for multi-character matches) and {@literal ?} (for
		 * single-character ones). As soon as an expression contains a dot ({@literal .}), the expression is applied to the
		 * entire trailing name. Expressions with a dot are applied to all name segments individually. In other words, an
		 * expression {@code foo} would match both the trailing names {@code foo}, {@code foo.bar}, and {@code bar.foo}.
		 *
		 * @param names must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #excluding(Collection)
		 */
		public Builder matching(Collection<String> names) {
			return including(matchesTrailingName(names));
		}

		/**
		 * Adds all packages matching the given predicate as named interface.
		 *
		 * @param inclusions must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #excluding(Predicate)
		 */
		public Builder including(Predicate<JavaPackage> inclusions) {

			Assert.notNull(inclusions, "Inclusions must not be null!");

			return new Builder(basePackage, recursive, inclusions, exclusions);
		}

		/**
		 * Excludes the packages with the given name expressions from being considered as named interface. See
		 * {@link #matching(String...)} for details on matching expressions.
		 *
		 * @param expressions must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #matching(String...)
		 */
		public Builder excluding(String... expressions) {

			Assert.notNull(expressions, "Expressions must not be null!");

			return excluding(List.of(expressions));
		}

		/**
		 * Excludes the packages with the given name expressions from being considered as named interface. See
		 * {@link #matching(Collection)} for details on matching expressions.
		 *
		 * @param expressions must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #matching(Collection)
		 */
		public Builder excluding(Collection<String> expressions) {
			return excluding(Predicate.not(matchesTrailingName(expressions)));
		}

		/**
		 * Excludes the packages matching the given {@link Predicate} from being considered as named interface.
		 *
		 * @param exclusions must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #including(Predicate)
		 */
		public Builder excluding(Predicate<JavaPackage> exclusions) {
			return new Builder(basePackage, recursive, inclusions, exclusions);
		}

		/**
		 * Creates a {@link NamedInterfaces} instance according to the builder setup. Will <em>always</em> include the
		 * unnamed interface established by the {@link JavaPackage} the {@link Builder} was set up for originally, as it's a
		 * required abstraction for every application module.
		 *
		 * @return will never be {@literal null}.
		 */
		public NamedInterfaces build() {

			var packages = recursive
					? basePackage.getSubPackages().stream()
					: basePackage.getDirectSubPackages().stream();

			var result = packages
					.filter(exclusions)
					.filter(inclusions)
					.map(it -> NamedInterface.of(basePackage.getTrailingName(it), it.getClasses()))
					.collect(collectingAndThen(toUnmodifiableList(), NamedInterfaces::new));

			return result.and(List.of(NamedInterface.unnamed(basePackage)));
		}

		private Predicate<JavaPackage> matchesTrailingName(Collection<String> names) {

			return it -> {

				var trailingName = PackageName.of(basePackage.getTrailingName(it));

				return names.stream().anyMatch(trailingName::nameContainsOrMatches);
			};
		}
	}
}
