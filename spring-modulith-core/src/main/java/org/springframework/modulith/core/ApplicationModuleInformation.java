/*
 * Copyright 2020-2024 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jmolecules.ddd.annotation.Module;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.ApplicationModule.Type;
import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Abstraction for low-level module information. Used to support different annotations to configure metadata about a
 * module.
 *
 * @author Oliver Drotbohm
 */
interface ApplicationModuleInformation {

	/**
	 * Creates a new {@link ApplicationModuleInformation} for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModuleInformation of(JavaPackage javaPackage) {

		var lookup = AnnotationLookup.of(javaPackage.toSingle(), __ -> true);

		return JMoleculesTypes.isPresent() && JMoleculesModule.supports(lookup)
				? new JMoleculesModule(lookup)
				: new SpringModulithModule(lookup);
	}

	/**
	 * Returns the display name to be used to describe the module.
	 *
	 * @return will never be {@literal null}.
	 */
	default Optional<String> getDisplayName() {
		return Optional.empty();
	}

	/**
	 * Returns all allowed dependencies.
	 *
	 * @return will never be {@literal null}.
	 */
	List<String> getDeclaredDependencies();

	/**
	 * Returns whether the module is considered open.
	 *
	 * @see org.springframework.modulith.ApplicationModule.Type
	 * @since 1.2
	 */
	boolean isOpen();

	/**
	 * An {@link ApplicationModuleInformation} for the jMolecules {@link Module} annotation.
	 *
	 * @author Oliver Drotbohm
	 * @see <a href="https://jmolecules.org">https://jMolecules.org</a>
	 */
	static class JMoleculesModule implements ApplicationModuleInformation {

		private final Optional<Module> annotation;

		public static boolean supports(AnnotationLookup lookup) {
			return lookup.lookup(Module.class).isPresent();
		}

		public <A extends Annotation> JMoleculesModule(AnnotationLookup lookup) {
			this.annotation = lookup.lookup(Module.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ApplicationModuleInformation#getDisplayName()
		 */
		@Override
		public Optional<String> getDisplayName() {

			Supplier<Optional<String>> fallback = () -> annotation //
					.map(Module::value) //
					.filter(StringUtils::hasText);

			return annotation //
					.map(Module::name) //
					.filter(StringUtils::hasText)
					.or(fallback);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.core.ApplicationModuleInformation#getDeclaredDependencies()
		 */
		@Override
		public List<String> getDeclaredDependencies() {
			return List.of(ApplicationModule.OPEN_TOKEN);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.core.ApplicationModuleInformation#isOpenModule()
		 */
		@Override
		public boolean isOpen() {
			return false;
		}
	}

	/**
	 * An {@link ApplicationModuleInformation} that inspects the {@link ApplicationModule} annotation.
	 *
	 * @author Oliver Drotbohm
	 */
	static class SpringModulithModule implements ApplicationModuleInformation {

		private final Optional<ApplicationModule> annotation;

		/**
		 * Whether the given {@link AnnotationLookup} supports this {@link ApplicationModuleInformation}.
		 *
		 * @param lookup must not be {@literal null}.
		 */
		public static boolean supports(AnnotationLookup lookup) {

			Assert.notNull(lookup, "Annotation lookup must not be null!");

			return lookup.lookup(ApplicationModule.class).isPresent();
		}

		/**
		 * Creates a new {@link SpringModulithModule} for the given {@link AnnotationLookup}.
		 *
		 * @param lookup must not be {@literal null}.
		 */
		public SpringModulithModule(AnnotationLookup lookup) {
			this.annotation = lookup.lookup(ApplicationModule.class);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.ApplicationModuleInformation#getDisplayName()
		 */
		@Override
		public Optional<String> getDisplayName() {

			return annotation //
					.map(ApplicationModule::displayName) //
					.filter(StringUtils::hasText);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.core.ApplicationModuleInformation#getDeclaredDependencies()
		 */
		@Override
		public List<String> getDeclaredDependencies() {

			return annotation //
					.map(it -> Arrays.stream(it.allowedDependencies())) //
					.orElse(Stream.of(ApplicationModule.OPEN_TOKEN)) //
					.toList();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.core.ApplicationModuleInformation#isOpenModule()
		 */
		@Override
		public boolean isOpen() {
			return annotation.map(it -> it.type().equals(Type.OPEN)).orElse(false);
		}
	}

	interface AnnotationLookup {

		static AnnotationLookup of(JavaPackage javaPackage,
				Predicate<JavaClass> typeSelector) {

			return new AnnotationLookup() {

				@Override
				public <A extends Annotation> Optional<A> lookup(Class<A> annotation) {
					return javaPackage.findAnnotation(annotation);
				}
			};
		}

		<A extends Annotation> Optional<A> lookup(Class<A> annotation);
	}
}
