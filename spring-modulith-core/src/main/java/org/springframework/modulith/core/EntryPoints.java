/*
 * Copyright 2026 the original author or authors.
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

import static org.springframework.modulith.core.SyntacticSugar.*;
import static org.springframework.modulith.core.Types.MessagingTypes.*;
import static org.springframework.modulith.core.Types.SpringTypes.*;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * All {@link EntryPoint}s of an {@link ApplicationModule}, i.e. methods or constructors that can trigger module
 * behavior from the outside.
 * <p>
 * There are two, independent ways for a method to qualify:
 * <ul>
 * <li>It's publicly accessible and declared on a type exposed by the module, i.e. reachable via a regular Java method
 * call from other modules.</li>
 * <li>It's invoked directly by framework infrastructure — an HTTP handler method, an application event listener, or a
 * message listener — regardless of whether its declaring type is exposed, as those are triggered independently of the
 * module's named interfaces.</li>
 * </ul>
 * This is a first, deliberately simple cut. It is expected to evolve, e.g. to cover further messaging technologies or
 * scheduled methods.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
class EntryPoints implements Iterable<EntryPoints.EntryPoint> {

	private static final List<String> FRAMEWORK_TRIGGERED_ANNOTATIONS = List.of( //
			AT_REQUEST_MAPPING, //
			AT_EVENT_LISTENER, //
			AT_TX_EVENT_LISTENER, //
			AT_KAFKA_LISTENER, //
			AT_RABBIT_LISTENER, //
			AT_JMS_LISTENER);

	private final ApplicationModule module;
	private final List<EntryPoint> entryPoints;

	/**
	 * Creates a new {@link EntryPoints} calculating the {@link EntryPoint}s of the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 */
	private EntryPoints(ApplicationModule module) {

		this.module = module;
		this.entryPoints = discoverEntryPoints();
	}

	/**
	 * Creates a new {@link EntryPoints} for the given, already qualified {@link JavaCodeUnit}s.
	 *
	 * @param module must not be {@literal null}.
	 * @param units must not be {@literal null}.
	 */
	private EntryPoints(ApplicationModule module, List<JavaCodeUnit> units) {

		this.module = module;
		this.entryPoints = units.stream().map(EntryPoint::new).toList();
	}

	/**
	 * Calculates the {@link EntryPoints} of the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static EntryPoints of(ApplicationModule module) {
		return new EntryPoints(module);
	}

	/**
	 * Returns whether the given {@link JavaCodeUnit} forms one of these {@link EntryPoint}s.
	 *
	 * @param unit must not be {@literal null}.
	 */
	boolean contains(JavaCodeUnit unit) {
		return entryPoints.stream().anyMatch(it -> it.method.equals(unit));
	}

	/**
	 * Returns a {@link Stream} of all {@link EntryPoint}s.
	 *
	 * @return will never be {@literal null}.
	 */
	Stream<EntryPoint> stream() {
		return entryPoints.stream();
	}

	/**
	 * Returns a new {@link EntryPoints} containing only the ones declared on the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	EntryPoints filter(JavaClass type) {
		return filter(it -> it.getType().equals(type));
	}

	/**
	 * Returns a new {@link EntryPoints} containing only the ones declared on the given type.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	EntryPoints filter(Class<?> type) {
		return filter(it -> it.getType().isEquivalentTo(type));
	}

	private EntryPoints filter(Predicate<EntryPoint> filter) {

		var units = entryPoints.stream() //
				.filter(filter) //
				.map(EntryPoint::getMethod) //
				.toList();

		return new EntryPoints(module, units);
	}

	/**
	 * Filters the given {@link TypeAware} candidates down to the ones actually (transitively) reachable from one of these
	 * {@link EntryPoint}s, returning the {@link EntryPoint}s found, each paired with the candidate(s) it potentially
	 * triggers the creation of.
	 * <p>
	 * For each candidate's {@link TypeAware#getCreations()}, we walk up the call graph via {@link JavaCall#getOrigin()}
	 * and its callers, recording every method along the way that forms one of these {@link EntryPoint}s as a potential
	 * trigger.
	 * <p>
	 * This is a first, deliberately simple cut of the algorithm used to select which of the discovered entry points are
	 * eventually surfaced. It is expected to evolve.
	 *
	 * @param <X> the type of candidate.
	 * @param candidates must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	<X extends TypeAware> MultiValueMap<EntryPoint, X> discover(List<X> candidates) {

		var result = new LinkedMultiValueMap<EntryPoint, X>();

		for (X candidate : candidates) {
			candidate.getCreations().stream() //
					.flatMap(this::discoverEntryPointsCreating) //
					.forEach(it -> result.add(it, candidate));
		}

		return result;
	}

	private Stream<EntryPoint> discoverEntryPointsCreating(JavaCall<?> call) {

		var origin = call.getOrigin();
		var self = preferred(origin).stream();

		// Keep walking up the call graph, but only through callers still within this module. Once a call leaves the
		// module, e.g. via the entry point just recorded above, further callers are none of this module's concern.
		var callers = origin.getAccessesToSelf().stream() //
				.filter(JavaCall.class::isInstance) //
				.map(JavaCall.class::cast) //
				.filter(it -> module.contains(it.getOrigin().getOwner())) //
				.flatMap(this::discoverEntryPointsCreating);

		return Stream.concat(self, callers);
	}

	/**
	 * Returns the {@link EntryPoint} to report for the given {@link JavaCodeUnit} — preferring one declared on an
	 * interface the given unit (if it's a method) overrides, provided that interface method forms an {@link EntryPoint}
	 * itself, e.g. because the interface is the module's actual, exposed contract and the given unit merely happens to be
	 * its (possibly internal) implementation.
	 *
	 * @param unit must not be {@literal null}.
	 */
	private Optional<EntryPoint> preferred(JavaCodeUnit unit) {

		if (!(unit instanceof JavaMethod method)) {
			return get(unit);
		}

		return entryPoints.stream() //
				.filter(it -> it.matches(method)) //
				.findFirst() //
				.or(() -> get(unit));
	}

	private Optional<EntryPoint> get(JavaCodeUnit unit) {
		return entryPoints.stream().filter(it -> it.method.equals(unit)).findFirst();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<EntryPoint> iterator() {
		return entryPoints.iterator();
	}

	/**
	 * Discovers all {@link EntryPoint}s of {@link #module}, i.e. every declared method or constructor that qualifies,
	 * plus any module interface method overridden by one of those that qualifies in its own right (see
	 * {@link #interfaceMethodsOverriddenBy(JavaMethod)}), so that it can be discovered — and preferred, see
	 * {@link #discoverEntryPointsCreating(JavaCall)} — during call graph traversal, too.
	 */
	private List<EntryPoint> discoverEntryPoints() {

		var declared = module.getClasses().stream() //
				.flatMap(this::codeUnitsOf) //
				.toList();

		var overriddenInterfaceMethods = declared.stream() //
				.filter(JavaMethod.class::isInstance) //
				.map(JavaMethod.class::cast) //
				.flatMap(this::interfaceMethodsOverriddenBy);

		return Stream.concat(declared.stream(), overriddenInterfaceMethods) //
				.distinct() //
				.filter(this::isEntryPoint) //
				.map(EntryPoint::new) //
				.toList();
	}

	private Stream<JavaCodeUnit> codeUnitsOf(JavaClass type) {
		return Stream.<JavaCodeUnit> concat(type.getMethods().stream(), type.getConstructors().stream());
	}

	private boolean isEntryPoint(JavaCodeUnit unit) {
		return isExposedMethod(unit) || isFrameworkTriggered(unit) || overridesEntryPoint(unit);
	}

	private boolean isExposedMethod(JavaCodeUnit unit) {
		return module.isExposed(unit.getOwner()) && unit.getModifiers().contains(JavaModifier.PUBLIC);
	}

	private boolean isFrameworkTriggered(JavaCodeUnit unit) {

		if (!(unit instanceof JavaMethod method)) {
			return false;
		}

		return FRAMEWORK_TRIGGERED_ANNOTATIONS.stream().anyMatch(it -> isAnnotatedWith(it).test(method))
				|| isApplicationListenerMethod(method);
	}

	/**
	 * Returns whether the given {@link JavaMethod} is the {@code onApplicationEvent(…)} callback of a Spring
	 * {@code ApplicationListener} implementation.
	 */
	private static boolean isApplicationListenerMethod(JavaMethod method) {

		return method.getName().equals("onApplicationEvent")
				&& method.getOwner().isAssignableTo(APPLICATION_LISTENER);
	}

	/**
	 * Returns whether the given {@link JavaCodeUnit} overrides a module interface method that itself forms an entry point
	 * — e.g. because it's declared on an exposed interface (regardless of whether the implementing type is itself
	 * exposed), or carries a framework annotation directly on the interface rather than repeated on every implementation.
	 *
	 * @see #interfaceMethodsOverriddenBy(JavaMethod)
	 */
	private boolean overridesEntryPoint(JavaCodeUnit unit) {

		return unit instanceof JavaMethod method && interfaceMethodsOverriddenBy(method) //
				.anyMatch(it -> isExposedMethod(it) || isFrameworkTriggered(it));
	}

	/**
	 * Returns the methods declared by any interface implemented by the given {@link JavaMethod}'s owner, that both
	 * belongs to {@link #module} and is overridden by the given method, matched by name and parameter types since
	 * ArchUnit doesn't resolve overriding relationships itself.
	 * <p>
	 * Restricted to interfaces the module actually owns — a foreign, e.g. framework, interface implemented by a module
	 * type (such as Spring's {@code ApplicationListener}) isn't part of the module's own contract, no matter how many
	 * types happen to implement it.
	 *
	 * @param method must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private Stream<JavaMethod> interfaceMethodsOverriddenBy(JavaMethod method) {

		var parameterTypeNames = method.getRawParameterTypes().stream().map(JavaClass::getName).toArray(String[]::new);

		return method.getOwner().getAllRawInterfaces().stream() //
				.filter(module::contains) //
				.map(it -> it.tryGetMethod(method.getName(), parameterTypeNames)) //
				.flatMap(Optional::stream);
	}

	/**
	 * A method or constructor that forms an entry point into the {@link ApplicationModule} that this {@link EntryPoints}
	 * instance was created for, i.e. code that can trigger module behavior from the outside, either because it's part of
	 * the module's exposed, public API, or because some framework infrastructure invokes it directly.
	 *
	 * @author Oliver Drotbohm
	 * @since 2.2
	 */
	class EntryPoint {

		private final JavaCodeUnit method;

		/**
		 * Creates a new {@link EntryPoint} for the given {@link JavaCodeUnit}.
		 *
		 * @param method must not be {@literal null}.
		 */
		private EntryPoint(JavaCodeUnit method) {

			Assert.notNull(method, "JavaCodeUnit must not be null!");

			this.method = method;
		}

		/**
		 * Returns the type the entry point is declared on.
		 *
		 * @return will never be {@literal null}.
		 */
		JavaClass getType() {
			return method.getOwner();
		}

		/**
		 * Returns the method or constructor that forms the entry point.
		 *
		 * @return will never be {@literal null}.
		 */
		JavaCodeUnit getMethod() {
			return method;
		}

		/**
		 * Returns whether this {@link EntryPoint}'s method is a module interface method overridden by the given candidate,
		 * i.e. whether this {@link EntryPoint} is a suitable, preferred stand-in for it.
		 *
		 * @param candidate must not be {@literal null}.
		 * @see EntryPoints#interfaceMethodsOverriddenBy(JavaMethod)
		 */
		boolean matches(JavaMethod candidate) {
			return interfaceMethodsOverriddenBy(candidate).anyMatch(method::equals);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {

			var type = FormattableType.of(getType()).getAbbreviatedFullName(module);
			var noParameters = method.getRawParameterTypes().isEmpty();

			return "%s.%s(%s)".formatted(type, method.getName(), noParameters ? "" : "…");
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

			if (!(obj instanceof EntryPoint that)) {
				return false;
			}

			return Objects.equals(this.method, that.method);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(method);
		}
	}
}
