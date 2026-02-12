/*
 * Copyright 2023-2026 the original author or authors.
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.modulith.events.RoutingTarget.ParsedRoutingTarget;
import org.springframework.modulith.events.RoutingTarget.RoutingTargetBuilder;
import org.springframework.util.Assert;

/**
 * Configuration for externalizing application events to messaging infrastructure.
 *
 * @author Oliver Drotbohm
 * @since 1.1
 * @see #externalizing()
 */
public interface EventExternalizationConfiguration {

	/**
	 * Creates a default {@link DefaultEventExternalizationConfiguration} with the following characteristics:
	 * <ul>
	 * <li>Only events that reside in any of the given packages and that are annotated with any supported
	 * {@code Externalized} annotation will be considered.</li>
	 * <li>Routing information is discovered from the {code Externalized} annotation and, if missing, will default to the
	 * application-local name of the event type. In other words, an event type {@code com.acme.myapp.mymodule.MyEvent}
	 * will result in a route {@code mymodule.MyEvent}.</li>
	 * </ul>
	 *
	 * @param packages must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @see Externalized
	 */
	public static Router defaults(Collection<String> packages) {

		Assert.notEmpty(packages, "Packages must not be null or empty!");

		Function<Object, RoutingTarget> router = it -> Optional.of(it)
				.flatMap(byApplicationLocalName(packages)::apply)
				.map(target -> mergeWithExternalizedAnnotation(it, target))
				.orElseGet(() -> byFullyQualifiedTypeName().apply(it));

		return DefaultEventExternalizationConfiguration.builder()
				.selectByPackagesAndFilter(packages, AnnotationTargetLookup::hasExternalizedAnnotation)
				.routeAll(router);
	}

	/**
	 * Creates a default {@link DefaultEventExternalizationConfiguration} with the following characteristics:
	 * <ul>
	 * <li>Only events that reside in any of the given packages and that are annotated with any supported
	 * {@code Externalized} annotation will be considered.</li>
	 * <li>Routing information is discovered from the {code Externalized} annotation and, if missing, will default to the
	 * application-local name of the event type. In other words, an event type {@code com.acme.myapp.mymodule.MyEvent}
	 * will result in a route {@code mymodule.MyEvent}.</li>
	 * </ul>
	 *
	 * @param packages must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @see Externalized
	 * @since 1.3
	 */
	public static Router defaults(String... packages) {
		return defaults(List.of(packages));
	}

	/**
	 * Creates a new {@link Selector} to define which events to externalize.
	 *
	 * @return will never be {@literal null}.
	 */
	public static Selector externalizing() {
		return new Selector();
	}

	/**
	 * Disables event externalization by not matching any events at all.
	 *
	 * @return will never be {@literal null}.
	 */
	public static EventExternalizationConfiguration disabled() {
		return externalizing().select(__ -> false).build();
	}

	/**
	 * A {@link Predicate} to select all events annotated as to be externalized. The currently supported annotations are:
	 * <ul>
	 * <li>Spring Modulith's {@link Externalized}</li>
	 * <li>jMolecules {@link org.jmolecules.event.annotation.Externalized} (if present on the classpath)</li>
	 * </ul>
	 *
	 * @return will never be {@literal null}.
	 */
	public static Predicate<Object> annotatedAsExternalized() {
		return event -> AnnotationTargetLookup.hasExternalizedAnnotation(event);
	}

	/**
	 * Creates a new routing that uses the application-local type name as target
	 *
	 * @param packages must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Function<Object, Optional<RoutingTarget>> byApplicationLocalName(Collection<String> packages) {

		Assert.notNull(packages, "Application packages must not be null!");

		return toEventType().andThen(type -> packages.stream()
				.filter(it -> type.getPackageName().startsWith(it))
				.map(it -> type.getName().substring(it.length() + 1))
				.findFirst()
				.map(RoutingTarget::forTarget)
				.map(RoutingTargetBuilder::withoutKey));
	}

	/**
	 * Returns a {@link Function} that looks up the target from the supported externalization annotations. The currently
	 * supported annotations are:
	 * <ul>
	 * <li>Spring Modulith's {@link Externalized}</li>
	 * <li>jMolecules {@link org.jmolecules.event.annotation.Externalized} (if present on the classpath)</li>
	 * </ul>
	 *
	 * @return will never be {@literal null}.
	 */
	public static Function<Object, Optional<ParsedRoutingTarget>> byExternalizedAnnotations() {
		return event -> AnnotationTargetLookup.of(event.getClass()).get();
	}

	/**
	 * Returns a {@link Function} that looks up the target from the fully-qualified type name of the event's type.
	 *
	 * @return will never be {@literal null}.
	 */
	public static Function<Object, RoutingTarget> byFullyQualifiedTypeName() {

		return toEventType()
				.andThen(Class::getName)
				.andThen(it -> RoutingTarget.forTarget(it).withoutKey());
	}

	/**
	 * Whether the configuration supports the given event. In other words, whether the given event is supposed to be
	 * externalized in the first place.
	 *
	 * @param event must not be {@literal null}.
	 * @return whether to externalize the given event.
	 */
	boolean supports(Object event);

	/**
	 * Map the event to be externalized before publishing it.
	 *
	 * @param event must not be {@literal null}.
	 * @return the mapped event.
	 */
	Object map(Object event);

	/**
	 * Determines the {@link RoutingTarget} for the given event based on the current configuration.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	RoutingTarget determineTarget(Object event);

	/**
	 * Returns the headers to be attached to the message sent out for the given event.
	 *
	 * @param event must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Map<String, Object> getHeadersFor(Object event);

	/**
	 * API to define which events are supposed to be selected for externalization.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.1
	 */
	public static class Selector {

		private static final Predicate<Object> DEFAULT_FILTER = it -> true;

		private final @Nullable Predicate<Object> predicate;

		/**
		 * Creates a new {@link Selector}.
		 */
		Selector() {
			this.predicate = DEFAULT_FILTER;
		}

		/**
		 * Selects events to externalize by applying the given {@link Predicate}.
		 *
		 * @param predicate will never be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router select(Predicate<Object> predicate) {
			return new Router(predicate);
		}

		/**
		 * Selects events to externalize by the given base package and all sub-packages.
		 *
		 * @param basePackage must not be {@literal null} or empty.
		 * @return will never be {@literal null}.
		 */
		public Router selectByPackage(String basePackage) {

			Assert.hasText(basePackage, "Base package must not be null or empty!");

			return select(it -> it.getClass().getPackageName().startsWith(basePackage));
		}

		/**
		 * Selects events to externalize by the package of the given type and all sub-packages.
		 *
		 * @param type must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByPackage(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");

			return selectByPackage(type.getPackageName());
		}

		/**
		 * Selects events to externalize by the given base packages (and their sub-packages) that match the given filter
		 * {@link Predicate}.
		 *
		 * @param basePackages must not be {@literal null} or empty.
		 * @param filter must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByPackagesAndFilter(Collection<String> basePackages,
				Predicate<Object> filter) {

			Assert.notEmpty(basePackages, "Base packages must not be null or empty!");
			Assert.notNull(filter, "Filter must not be null!");

			BiPredicate<Object, String> matcher = (event, reference) -> event.getClass().getPackageName()
					.startsWith(reference);
			Predicate<Object> residesInPackage = it -> basePackages.stream().anyMatch(inner -> matcher.test(it, inner));

			return select(residesInPackage.and(filter));
		}

		/**
		 * Selects events to be externalized by inspecting the event type for the given annotation.
		 *
		 * @param type the annotation type to find on the event type, must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByAnnotation(Class<? extends Annotation> type) {

			Assert.notNull(type, "Annotation type must not be null!");

			return select(it -> AnnotatedElementUtils.hasAnnotation(it.getClass(), type));
		}

		/**
		 * Selects events to be externalized by type.
		 *
		 * @param type the type that events to be externalized need to implement, must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByType(Class<?> type) {

			Assert.notNull(type, "Type must not be null!");

			return select(type::isInstance);
		}

		/**
		 * Selects events to be externalized by the given {@link Predicate}.
		 *
		 * @param predicate must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router selectByType(Predicate<Class<?>> predicate) {

			Assert.notNull(predicate, "Predicate must not be null!");

			return select(it -> predicate.test(it.getClass()));
		}

		/**
		 * Selects events by the presence of an annotation of the given type and routes based on the given router
		 * {@link Function}.
		 *
		 * @param <T> the annotation type.
		 * @param annotationType the annotation type, must not be {@literal null}.
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public <T extends Annotation> Router selectAndRoute(Class<T> annotationType,
				Function<T, String> router) {

			Assert.notNull(annotationType, "Annotation type must not be null!");
			Assert.notNull(router, "Router must not be null!");

			Function<Object, T> extractor = it -> findAnnotation(it, annotationType);

			return selectByAnnotation(annotationType)
					.routeAll(it -> extractor
							.andThen(router)
							.andThen(RoutingTarget::parse)
							.andThen(target -> target.withFallback(byFullyQualifiedTypeName().apply(it)))
							.apply(it));
		}

		/**
		 * Selects events by the presence of an annotation of the given type and routes based on the given router
		 * {@link BiFunction} that also gets the event type to build up a complete {@link RoutingTarget}.
		 *
		 * @param <T> the annotation type.
		 * @param annotationType must not be {@literal null}.
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public <T extends Annotation> Router selectAndRoute(Class<T> annotationType,
				BiFunction<Object, T, RoutingTarget> router) {

			Assert.notNull(annotationType, "Annotation type must not be null!");
			Assert.notNull(router, "Router must not be null!");

			return selectByAnnotation(annotationType)
					.routeAll(it -> router.apply(it, findAnnotation(it, annotationType)));
		}

		private static <T extends Annotation> T findAnnotation(Object event, Class<T> annotationType) {
			return findMergedAnnotation(event.getClass(), annotationType);
		}
	}

	/**
	 * API to define the event routing.
	 *
	 * @author Oliver Drotbohm
	 * @since 1.1
	 */
	public static class Router {

		private static final Function<Object, RoutingTarget> DEFAULT_ROUTER = it -> {
			return mergeWithExternalizedAnnotation(it, byFullyQualifiedTypeName().apply(it));
		};

		private final Predicate<Object> filter;
		private final Function<Object, Object> mapper;
		private final Function<Object, RoutingTarget> router;
		private final Function<Object, Map<String, Object>> headers;

		/**
		 * Creates a new {@link Router} for the given selector {@link Predicate} and mapper and router {@link Function}s.
		 *
		 * @param filter must not be {@literal null}.
		 * @param mapper must not be {@literal null}.
		 * @param router must not be {@literal null}.
		 * @param headers must not be {@literal null}.
		 */
		Router(Predicate<Object> filter, Function<Object, Object> mapper, Function<Object, RoutingTarget> router,
				Function<Object, Map<String, Object>> headers) {

			Assert.notNull(filter, "Selector must not be null!");
			Assert.notNull(mapper, "Mapper must not be null!");
			Assert.notNull(router, "Router must not be null!");
			Assert.notNull(headers, "Headers extractor must not be null!");

			this.filter = filter;
			this.mapper = mapper;
			this.router = router;
			this.headers = headers;
		}

		/**
		 * Creates a new {@link Router} for the given selector filter.
		 *
		 * @param filter must not be {@literal null}.
		 */
		Router(Predicate<Object> filter) {
			this(filter, Function.identity(), DEFAULT_ROUTER, it -> Collections.emptyMap());
		}

		/**
		 * Registers a new mapping {@link Function} replacing the old one entirely.
		 *
		 * @param mapper must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #mapping(Class, Function)
		 */
		public Router mapping(Function<Object, Object> mapper) {

			Assert.notNull(mapper, "Mapper must not be null!");

			return new Router(filter, mapper, router, headers);
		}

		/**
		 * Registers a type-specific mapping function. Events not matching that type will still fall back to the global
		 * mapping function defined.
		 *
		 * @param <T> the type to handle.
		 * @param type the type to handle, must not be {@literal null}.
		 * @param mapper the mapping function, must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #mapping(Function)
		 */
		public <T> Router mapping(Class<T> type, Function<T, Object> mapper) {

			Assert.notNull(type, "Type must not be null!");
			Assert.notNull(mapper, "Mapper must not be null!");

			Function<Object, Object> combined = it -> toOptional(type, it)
					.map(mapper::apply)
					.orElse(it);

			return new Router(filter, this.mapper.compose(combined), router, headers);
		}

		/**
		 * Registers the given function to extract headers from the events to be externalized. Will reset the entire header
		 * extractor arrangement. For type-specific extractions, see {@link #headers(Class, Function)}.
		 *
		 * @param extractor must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #headers(Class, Function)
		 * @since 1.3
		 */
		public Router headers(Function<Object, Map<String, Object>> extractor) {

			Assert.notNull(extractor, "Headers extractor must not be null!");

			return new Router(filter, mapper, router, extractor);
		}

		/**
		 * Registers the given type-specific function to extract headers from the events to be externalized.
		 *
		 * @param extractor must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @since 1.3
		 */
		public <T> Router headers(Class<T> type, Function<T, Map<String, Object>> extractor) {

			Assert.notNull(type, "Type must not be null!");
			Assert.notNull(extractor, "Headers extractor must not be null!");

			Function<Object, Map<String, Object>> combined = it -> toOptional(type, it)
					.map(extractor::apply)
					.orElseGet(() -> this.headers.apply(it));

			return new Router(filter, mapper, router, combined);
		}

		/**
		 * Configures the routing to rather use the mapping result rather than the original event instance.
		 *
		 * @return will never be {@literal null}.
		 */
		public Router routeMapped() {
			return new Router(filter, mapper, router.compose(mapper), headers);
		}

		/**
		 * Routes all events based on the given function.
		 *
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router routeAll(Function<Object, RoutingTarget> router) {

			Assert.notNull(router, "Router must not be null!");

			return new Router(filter, mapper, router, headers);
		}

		/**
		 * Registers a router function for the events of the given specific type.
		 *
		 * @param <T> the type to handle
		 * @param type must not be {@literal null}.
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public <T> Router route(Class<T> type, Function<T, RoutingTarget> router) {

			Assert.notNull(type, "Type must not be null!");
			Assert.notNull(router, "Router must not be null!");

			Function<Object, RoutingTarget> adapted = it -> toOptional(type, it)
					.map(router::apply)
					.orElseGet(() -> this.router.apply(it));

			return new Router(filter, mapper, adapted, headers);
		}

		/**
		 * Registers a {@link BiFunction} to resolve the key for a {@link RoutingTarget} based on the event instance. The
		 * actual target will have been resolved through the currently configured, global router. To dynamically define
		 * full, type-specific resolution of a {@link RoutingTarget}, see {@link #route(Class, Function)}.
		 *
		 * @param <T> the type to handle.
		 * @param type the type to configure the key extraction for, must not be {@literal null}.
		 * @param extractor the key extractor, must not be {@literal null}.
		 * @return will never be {@literal null}.
		 * @see #route(Class, Function)
		 */
		public <T> Router routeKey(Class<T> type, Function<T, String> extractor) {

			Assert.notNull(type, "Type must not be null!");
			Assert.notNull(extractor, "Extractor must not be null!");

			Function<Object, RoutingTarget> adapted = it -> toOptional(type, it)
					.map(t -> this.router.apply(t).withKey(extractor.apply(t)))
					.orElseGet(() -> this.router.apply(it));

			return new Router(filter, mapper, adapted, headers);
		}

		/**
		 * Routes by extracting an {@link Optional} route from the event. If {@link Optional#empty()} is returned by the
		 * function, we will fall back to the configured default routing.
		 *
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public EventExternalizationConfiguration routeOptional(Function<Object, Optional<RoutingTarget>> router) {

			Assert.notNull(router, "Router must not be null!");

			Function<Object, RoutingTarget> adapted = it -> router.apply(it).orElseGet(() -> this.router.apply(it));

			return new Router(filter, mapper, adapted, headers).build();
		}

		/**
		 * Routes by extracting an {@link Optional} route from the event type. If {@link Optional#empty()} is returned by
		 * the function, we will fall back to the configured general routing.
		 *
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public EventExternalizationConfiguration routeOptionalByType(
				Function<Class<?>, Optional<RoutingTarget>> router) {

			Assert.notNull(router, "Router must not be null!");

			return routeOptional(it -> router.apply(it.getClass()));
		}

		/**
		 * Routes all messages based on the event type only.
		 *
		 * @param router must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public Router routeAllByType(Function<Class<?>, RoutingTarget> router) {

			Assert.notNull(router, "Router must not be null!");

			return new Router(filter, mapper, it -> router.apply(it.getClass()), headers);
		}

		/**
		 * Creates a new {@link EventExternalizationConfiguration} reflecting the current configuration.
		 *
		 * @return will never be {@literal null}.
		 */
		public EventExternalizationConfiguration build() {
			return new DefaultEventExternalizationConfiguration(filter, mapper, router, headers);
		}

		private static <T> Optional<T> toOptional(Class<T> type, Object source) {

			return Optional.of(source)
					.filter(type::isInstance)
					.map(type::cast);
		}
	}

	/**
	 * Detects standard {@code Externalized} annotations on the given event and applies the given {@link RoutingTarget}'s
	 * target to the one found in the annotation, if the latter does not declare a target itself.
	 *
	 * @param event must not be {@literal null}.
	 * @param target must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private static RoutingTarget mergeWithExternalizedAnnotation(Object event, RoutingTarget target) {

		Assert.notNull(event, "Event must not be null!");
		Assert.notNull(target, "RoutingTarget must not be null!");

		return byExternalizedAnnotations().apply(event)
				.map(it -> it.withFallback(target))
				.orElse(target);
	}

	private static Function<Object, Class<?>> toEventType() {
		return event -> event.getClass();
	}
}
