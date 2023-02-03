/*
 * Copyright 2019-2023 the original author or authors.
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
	public Object getModulithSource() {
		return getSource();
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
	 * @see org.springframework.modulith.model.ModulithMetadata#getAdditionalPackages()
	 */
	@Override
	public List<String> getAdditionalPackages() {
		return Arrays.asList(annotation.additionalPackages());
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
	 * @see org.springframework.modulith.model.ModulithMetadata#getSharedModuleNames()
	 */
	@Override
	public Stream<String> getSharedModuleNames() {
		return Arrays.stream(annotation.sharedModules());
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
}
