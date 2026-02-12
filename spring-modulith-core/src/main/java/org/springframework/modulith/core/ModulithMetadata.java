/*
 * Copyright 2019-2026 the original author or authors.
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.modulith.Modulith;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.core.Types.SpringTypes;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Core metadata about the modulithic application.
 *
 * @author Oliver Drotbohm
 */
public interface ModulithMetadata {

	static final String ANNOTATION_MISSING = "Modules can only be retrieved from a root type, but %s is not annotated with either @%s, @%s or @%s!";
	static final String WITH_ANNOTATIONS = ANNOTATION_MISSING.formatted("%s", Modulith.class.getSimpleName(),
			Modulithic.class.getSimpleName(), SpringTypes.AT_SPRING_BOOT_APPLICATION);

	/**
	 * Creates a new {@link ModulithMetadata} for the given annotated type. Expects the type either be annotated with
	 * {@link Modulith}, {@link Modulithic} or {@link org.springframework.boot.autoconfigure.SpringBootApplication}.
	 *
	 * @param annotated must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @throws IllegalArgumentException in case none of the above mentioned annotations is present on the given type.
	 */
	public static ModulithMetadata of(Class<?> annotated) {

		Assert.notNull(annotated, "Annotated type must not be null!");

		return AnnotationModulithMetadata.of(annotated)
				.or(() -> SpringBootModulithMetadata.of(annotated))
				.orElseThrow(() -> new IllegalArgumentException(WITH_ANNOTATIONS.formatted(annotated.getSimpleName())));
	}

	/**
	 * Creates a new {@link ModulithMetadata} instance for the given package. Inspects the package for classes annotated
	 * with {@link Modulithic} to pick up additional configuration.
	 *
	 * @param javaPackage must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static ModulithMetadata of(String javaPackage) {

		var provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AnnotationTypeFilter(Modulithic.class));

		Set<BeanDefinition> candidates = provider.findCandidateComponents(javaPackage);

		if (candidates.size() != 1) {
			return SpringBootModulithMetadata.of(javaPackage);
		}

		var definition = candidates.iterator().next();
		var className = definition.getBeanClassName();

		if (className == null) {
			throw new IllegalStateException("No bean class name found on BeanDefinition %s!".formatted(definition));
		}

		return of(ClassUtils.resolveClassName(className, ModulithMetadata.class.getClassLoader()));
	}

	/**
	 * Returns the source of the Spring Modulith setup. Either a type or a package.
	 *
	 * @return will never be {@literal null}.
	 */
	Object getSource();

	/**
	 * Whether to use fully-qualified module names, i.e. rather use the fully-qualified package name instead of the local
	 * one.
	 *
	 * @return
	 */
	boolean useFullyQualifiedModuleNames();

	/**
	 * Returns the {@link ApplicationModuleIdentifier}s of shared modules, i.e. modules that are supposed to always be
	 * included in bootstraps.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Stream<ApplicationModuleIdentifier> getSharedModuleIdentifiers();

	/**
	 * Returns the name of the system.
	 *
	 * @return will never be {@literal null}.
	 */
	Optional<String> getSystemName();

	/**
	 * Returns all base packages of the modulith.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.2
	 */
	List<String> getBasePackages();
}
