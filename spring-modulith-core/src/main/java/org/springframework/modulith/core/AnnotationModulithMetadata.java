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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.Modulithic;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ModulithMetadata} backed by a {@link Modulithic} annotated type.
 *
 * @author Oliver Drotbohm
 */
class AnnotationModulithMetadata implements ModulithMetadata {

	private final Class<?> modulithType;
	private final Modulithic annotation;
	private final String basePackage;

	/**
	 * Creates a new {@link AnnotationModulithMetadata} for the given type and annotation.
	 *
	 * @param modulithType must not be {@literal null}.
	 * @param annotation must not be {@literal null}.
	 */
	private AnnotationModulithMetadata(Class<?> modulithType, Modulithic annotation) {

		Assert.notNull(modulithType, "Type must not be null!");
		Assert.notNull(annotation, "Annotation must not be null!");

		this.modulithType = modulithType;
		this.annotation = annotation;
		this.basePackage = modulithType.getPackageName();
	}

	/**
	 * Creates a {@link ModulithMetadata} inspecting {@link Modulithic} annotation or return {@link Optional#empty()} if
	 * the type given does not carry the annotation.
	 *
	 * @param annotated must not be {@literal null}.
	 * @return
	 */
	public static Optional<ModulithMetadata> of(Class<?> annotated) {

		Assert.notNull(annotated, "Modulith type must not be null!");

		Modulithic annotation = AnnotatedElementUtils.findMergedAnnotation(annotated, Modulithic.class);

		return Optional.ofNullable(annotation) //
				.map(it -> new AnnotationModulithMetadata(annotated, it));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.ModulithMetadata#getModulithSource()
	 */
	@Override
	public Object getSource() {
		return modulithType;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.ModulithMetadata#useFullyQualifiedModuleNames()
	 */
	@Override
	public boolean useFullyQualifiedModuleNames() {
		return annotation.useFullyQualifiedModuleNames();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ModulithMetadata#getSharedModuleIdentifiers()
	 */
	@Override
	public Stream<ApplicationModuleIdentifier> getSharedModuleIdentifiers() {
		return Arrays.stream(annotation.sharedModules()).map(ApplicationModuleIdentifier::of);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.ModulithMetadata#getSystemName()
	 */
	@Override
	public Optional<String> getSystemName() {

		return Optional.of(annotation.systemName()) //
				.filter(StringUtils::hasText) //
				.or(() -> Optional.of(modulithType.getSimpleName()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ModulithMetadata#getBasePackages()
	 */
	@Override
	public List<String> getBasePackages() {

		var result = new ArrayList<String>();
		result.add(basePackage);

		var name = PackageName.of(basePackage);

		var additionalPackages = Stream.of(annotation.additionalPackages())
				.map(PackageName::of)
				.distinct()
				.toList();

		// Reject additional packages enclosing the application root.
		additionalPackages.forEach(candidate -> Assert.isTrue(!candidate.isParentPackageOf(name),
				() -> "Additional package '%s' must not enclose application root package '%s'!"
						.formatted(candidate.getName(), basePackage)));

		// Preserve the existing behavior of ignoring packages nested in the application root.
		var validAdditionalPackages = additionalPackages.stream()
				.filter(Predicate.not(name::contains))
				.toList();

		// Ignore additional packages already covered by another additional package.
		validAdditionalPackages.stream()
				.filter(candidate -> validAdditionalPackages.stream()
						.noneMatch(other -> other.isParentPackageOf(candidate)))
				.map(PackageName::getName)
				.forEach(result::add);

		return result;
	}
}
