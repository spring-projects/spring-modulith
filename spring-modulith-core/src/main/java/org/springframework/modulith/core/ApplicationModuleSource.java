/*
 * Copyright 2024-2025 the original author or authors.
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The source of an {@link ApplicationModule}. Essentially a {@link JavaPackage} and associated naming strategy for the
 * module. This will be used when constructing sources from a base package and an
 * {@link ApplicationModuleDetectionStrategy} so that the names of the module to be created for the detected packages
 * become the trailing name underneath the base package. For example, scanning from {@code com.acme}, an
 * {@link ApplicationModule} located in {@code com.acme.foo.bar} would be named {@code foo.bar}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
public class ApplicationModuleSource {

	private static final ApplicationModuleSourceMetadata ANNOTATION_IDENTIFIER_SOURCE = ApplicationModuleSourceMetadata
			.delegating(
					JMoleculesTypes.getIdentifierSource(),
					ApplicationModuleSourceMetadata.forAnnotation(ApplicationModule.class, ApplicationModule::id));

	private final JavaPackage moduleBasePackage;
	private final ApplicationModuleIdentifier identifier;
	private final Function<ApplicationModuleInformation, NamedInterfaces> namedInterfacesFactory;

	/**
	 * Creates a new {@link ApplicationModuleSource} for the given module base package and module name.
	 *
	 * @param moduleBasePackage must not be {@literal null}.
	 * @param moduleName must not be {@literal null} or empty.
	 * @param namedInterfacesFactory must not be {@literal null}.
	 */
	private ApplicationModuleSource(JavaPackage moduleBasePackage, ApplicationModuleIdentifier identifier,
			Function<ApplicationModuleInformation, NamedInterfaces> namedInterfacesFactory) {

		Assert.notNull(moduleBasePackage, "JavaPackage must not be null!");
		Assert.notNull(identifier, "ApplicationModuleIdentifier must not be null!");
		Assert.notNull(namedInterfacesFactory, "NamedInterfaces factory must not be null!");

		this.moduleBasePackage = moduleBasePackage;
		this.identifier = identifier;
		this.namedInterfacesFactory = namedInterfacesFactory;
	}

	/**
	 * Returns a {@link Stream} of {@link ApplicationModuleSource}s by applying the given
	 * {@link ApplicationModuleDetectionStrategy} to the given base package.
	 *
	 * @param rootPackage must not be {@literal null}.
	 * @param strategy must not be {@literal null}.
	 * @param fullyQualifiedModuleNames whether to use fully qualified module names.
	 * @return will never be {@literal null}.
	 */
	public static Stream<ApplicationModuleSource> from(JavaPackage rootPackage,
			ApplicationModuleDetectionStrategy strategy, boolean fullyQualifiedModuleNames) {

		Assert.notNull(rootPackage, "Root package must not be null!");
		Assert.notNull(strategy, "ApplicationModuleDetectionStrategy must not be null!");

		return strategy.getModuleBasePackages(rootPackage)
				.flatMap(ANNOTATION_IDENTIFIER_SOURCE::withNestedPackages)
				.map(it -> {

					var id = ANNOTATION_IDENTIFIER_SOURCE.lookupIdentifier(it.toSingle())
							.orElseGet(() -> ApplicationModuleIdentifier.of(
									fullyQualifiedModuleNames ? it.getName() : rootPackage.getTrailingName(it)));

					return new ApplicationModuleSource(it, id, (info) -> strategy.detectNamedInterfaces(it, info));
				});
	}

	/**
	 * Creates a new {@link ApplicationModuleSource} for the given {@link JavaPackage} and name.
	 *
	 * @param pkg must not be {@literal null}.
	 * @param identifier must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	static ApplicationModuleSource from(JavaPackage pkg, String identifier) {
		return new ApplicationModuleSource(pkg, ApplicationModuleIdentifier.of(identifier),
				(info) -> NamedInterfaces.of(pkg, info));
	}

	/**
	 * Returns the base package for the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public JavaPackage getModuleBasePackage() {
		return moduleBasePackage;
	}

	/**
	 * Returns the {@link ApplicationModuleIdentifier} to be used for the module.
	 *
	 * @return will never be {@literal null}.
	 */
	public ApplicationModuleIdentifier getIdentifier() {
		return identifier;
	}

	/**
	 * Returns all {@link NamedInterfaces} for the given {@link ApplicationModuleInformation}.
	 *
	 * @param information must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.4
	 */
	public NamedInterfaces getNamedInterfaces(ApplicationModuleInformation information) {

		Assert.notNull(information, "ApplicationModuleInformation must not be null!");

		return namedInterfacesFactory.apply(information);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (obj == this) {
			return true;
		}

		if (!(obj instanceof ApplicationModuleSource that)) {
			return false;
		}

		return Objects.equals(this.identifier, that.identifier)
				&& Objects.equals(this.moduleBasePackage, that.moduleBasePackage);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(identifier, moduleBasePackage);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ApplicationModuleSource(" + identifier + ", " + moduleBasePackage.getName() + ")";
	}

	/**
	 * An intermediate abstraction to detect both the {@link ApplicationModuleIdentifier} and potentially nested module
	 * declarations for the {@link JavaPackage}s returned from the first pass of module detection.
	 *
	 * @author Oliver Drotbohm
	 * @see ApplicationModuleDetectionStrategy
	 */
	interface ApplicationModuleSourceMetadata {

		/**
		 * Returns an optional {@link ApplicationModuleIdentifier} obtained by the annotation on the given package.
		 *
		 * @param pkg must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		Optional<ApplicationModuleIdentifier> lookupIdentifier(JavaPackage pkg);

		/**
		 * Return a {@link Stream} of {@link JavaPackage}s that are
		 *
		 * @param pkg must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		Stream<JavaPackage> withNestedPackages(JavaPackage pkg);

		/**
		 * Creates a new {@link ApplicationModuleSourceFactory} detecting the {@link ApplicationModuleIdentifier} based on a
		 * particular annotation's attribute. It also detects nested {@link JavaPackage}s annotated with the given
		 * annotation as nested module base packages.
		 *
		 * @param <T> an annotation type
		 * @param annotation must not be {@literal null}.
		 * @param extractor must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		static <T extends Annotation> ApplicationModuleSourceMetadata forAnnotation(Class<T> annotation,
				Function<T, String> extractor) {

			Assert.notNull(annotation, "Annotation type must not be null!");
			Assert.notNull(extractor, "Attribute extractor must not be null!");

			return new ApplicationModuleSourceMetadata() {

				@Override
				public Optional<ApplicationModuleIdentifier> lookupIdentifier(JavaPackage pkg) {

					return pkg.getAnnotation(annotation)
							.map(extractor)
							.filter(StringUtils::hasText)
							.map(ApplicationModuleIdentifier::of);
				}

				@Override
				public Stream<JavaPackage> withNestedPackages(JavaPackage pkg) {
					return pkg.getSubPackagesAnnotatedWith(annotation);
				}
			};
		}

		/**
		 * Returns an {@link ApplicationModuleSourceFactory} delegating to the given ones, chosing the first identifier
		 * found and assembling nested packages of all delegate {@link ApplicationModuleSourceFactory} instances.
		 *
		 * @param delegates must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		private static ApplicationModuleSourceMetadata delegating(ApplicationModuleSourceMetadata... delegates) {

			return new ApplicationModuleSourceMetadata() {

				@Override
				public Stream<JavaPackage> withNestedPackages(JavaPackage pkg) {

					return Stream.concat(Stream.of(pkg), Stream.of(delegates)
							.filter(Objects::nonNull)
							.flatMap(it -> it.withNestedPackages(pkg)));
				}

				@Override
				public Optional<ApplicationModuleIdentifier> lookupIdentifier(JavaPackage pkg) {

					return Stream.of(delegates)
							.filter(Objects::nonNull)
							.flatMap(it -> it.lookupIdentifier(pkg).stream())
							.findFirst();
				}
			};
		}
	}
}
