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
package org.springframework.modulith.events;

import static org.springframework.core.annotation.AnnotatedElementUtils.*;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.modulith.events.RoutingTarget.ParsedRoutingTarget;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * An annotation based target lookup strategy to enable caching of the function lookups that involve classpath checks.
 * The currently supported annotations are:
 * <ul>
 * <li>Spring Modulith's {@link Externalized}</li>
 * <li>jMolecules {@link org.jmolecules.event.annotation.Externalized} (if present on the classpath)</li>
 * </ul>
 *
 * @author Oliver Drotbohm
 * @since 1.1
 */
class AnnotationTargetLookup implements Supplier<Optional<ParsedRoutingTarget>> {

	private static Map<Class<?>, AnnotationTargetLookup> LOOKUPS = new ConcurrentReferenceHashMap<>(25);
	private static final String JMOLECULES_EXTERNALIZED = "org.jmolecules.event.annotation.Externalized";
	private static final Class<? extends Annotation> JMOLECULES_ANNOTATION = loadJMoleculesExternalizedIfPresent();

	private final Class<?> type;
	private final Supplier<Optional<ParsedRoutingTarget>> lookup;

	/**
	 * Creates a new {@link AnnotationTargetLookup} for the given type.
	 *
	 * @param type must not be {@literal null}.
	 */
	private AnnotationTargetLookup(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		this.type = type;
		this.lookup = firstMatching(fromJMoleculesExternalized(), fromModulithExternalized());
	}

	/**
	 * Returns the {@link AnnotationTargetLookup} for the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static AnnotationTargetLookup of(Class<?> type) {
		return LOOKUPS.computeIfAbsent(type, AnnotationTargetLookup::new);
	}

	/**
	 * Returns whether the given event is annotated with a supported {@code Externalized} annotation.
	 *
	 * @param event must not be {@literal null}.
	 */
	static boolean hasExternalizedAnnotation(Object event) {

		Assert.notNull(event, "Event must not be null!");

		var type = event.getClass();

		return hasAnnotation(type, Externalized.class)
				|| JMOLECULES_ANNOTATION != null && hasAnnotation(type, JMOLECULES_ANNOTATION);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.function.Supplier#get()
	 */
	@Override
	public Optional<ParsedRoutingTarget> get() {
		return lookup.get();
	}

	/**
	 * Creates a {@link Supplier} to lookup the target from Spring Modulith's {@link Externalized} annotation.
	 *
	 * @return will never be {@literal null}.
	 */
	private Supplier<Optional<ParsedRoutingTarget>> fromModulithExternalized() {
		return () -> lookupTarget(Externalized.class, Externalized::target);
	}

	/**
	 * Creates a {@link Supplier} to lookup the target from jMolecules
	 * {@link org.jmolecules.event.annotation.Externalized} annotation if present on the classpath.
	 *
	 * @return will never be {@literal null}.
	 */
	private Supplier<Optional<ParsedRoutingTarget>> fromJMoleculesExternalized() {

		return JMOLECULES_ANNOTATION == null
				? () -> Optional.empty()
				: () -> lookupTarget(org.jmolecules.event.annotation.Externalized.class,
						org.jmolecules.event.annotation.Externalized::target,
						org.jmolecules.event.annotation.Externalized::value);
	}

	/**
	 * Returns a new {@link Function} that chains the given lookup functions until one returns a non-empty
	 * {@link Optional}.
	 *
	 * @param functions must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	@SafeVarargs
	private Supplier<Optional<ParsedRoutingTarget>> firstMatching(
			Supplier<Optional<ParsedRoutingTarget>>... functions) {

		return () -> Arrays.stream(functions)
				.reduce(Optional.empty(), (current, function) -> current.or(() -> function.get()), (l, r) -> r);
	}

	/**
	 * Looks up the target from the given annotation applying the given extractors aborting if a non-empty {@link String}
	 * is found.
	 *
	 * @param <T> the annotation type
	 * @param annotation must not be {@literal null}.
	 * @param extractors must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	@SafeVarargs
	private <T extends Annotation> Optional<ParsedRoutingTarget> lookupTarget(Class<T> annotation,
			Function<T, String>... extractors) {

		return Optional.ofNullable(findMergedAnnotation(type, annotation))
				.stream()
				.flatMap(it -> Arrays.stream(extractors)
						.map(function -> function.apply(it))
						.filter(Predicate.not(String::isBlank)))
				.findFirst()
				.map(RoutingTarget::parse);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Annotation> loadJMoleculesExternalizedIfPresent() {

		var classLoader = DefaultEventExternalizationConfiguration.class.getClassLoader();

		if (ClassUtils.isPresent(JMOLECULES_EXTERNALIZED, classLoader)) {

			try {
				return (Class<? extends Annotation>) ClassUtils.forName(JMOLECULES_EXTERNALIZED, classLoader);
			} catch (Exception o_O) {
				return null;
			}
		}

		return null;
	}
}
