/*
 * Copyright 2019-2025 the original author or authors.
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.core.Types.SpringTypes;
import org.springframework.util.Assert;

/**
 * Creates a new {@link ModulithMetadata} representing the defaults of {@link org.springframework.modulith.Modulithic}
 * but without the annotation present.
 *
 * @author Oliver Drotbohm
 */
class SpringBootModulithMetadata implements ModulithMetadata {

	private static final @Nullable Class<? extends Annotation> AT_SPRING_BOOT_APPLICATION = Types
			.loadIfPresent(SpringTypes.AT_SPRING_BOOT_APPLICATION);

	private final Object source;
	private final String basePackage;
	private final @Nullable String systemName;

	/**
	 * Creates a new {@link SpringBootModulithMetadata} for the given source.
	 *
	 * @param source must not be {@literal null}.
	 * @param basePackage must not be {@literal null}.
	 * @param systemName can be {@literal null}.
	 */
	private SpringBootModulithMetadata(Object source, String basePackage, @Nullable String systemName) {

		Assert.notNull(source, "Source must not be null!");
		Assert.notNull(basePackage, "Base package must not be null!");

		this.source = source;
		this.basePackage = basePackage;
		this.systemName = systemName;
	}

	/**
	 * Creates a new {@link ModulithMetadata} representing the defaults of a class annotated with
	 * {@link org.springframework.boot.autoconfigure.SpringBootApplication} but not customized with
	 * {@link org.springframework.modulith.Modulithic} or {@link org.springframework.modulith.Modulith}.
	 *
	 * @param annotated must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Optional<ModulithMetadata> of(Class<?> annotated) {

		Assert.notNull(annotated, "Annotated type must not be null!");

		return Optional.ofNullable(AT_SPRING_BOOT_APPLICATION) //
				.filter(it -> AnnotatedElementUtils.hasAnnotation(annotated, it)) //
				.map(__ -> new SpringBootModulithMetadata(annotated, annotated.getPackageName(), annotated.getSimpleName()));
	}

	/**
	 * Creates a new {@link ModulithMetadata} from the given package name.
	 *
	 * @param javaPackage must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static ModulithMetadata of(String javaPackage) {

		Assert.hasText(javaPackage, "Package name must not be null or empty!");

		return new SpringBootModulithMetadata(javaPackage, javaPackage, null);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.ModulithMetadata#getModulithSource()
	 */
	@Override
	public Object getSource() {
		return source;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.ModulithMetadata#useFullyQualifiedModuleNames()
	 */
	@Override
	public boolean useFullyQualifiedModuleNames() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ModulithMetadata#getSharedModuleIdentifiers()
	 */
	@Override
	public Stream<ApplicationModuleIdentifier> getSharedModuleIdentifiers() {
		return Stream.empty();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.model.ModulithMetadata#getSystemName()
	 */
	@Override
	public Optional<String> getSystemName() {
		return Optional.ofNullable(systemName);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.core.ModulithMetadata#getBasePackages()
	 */
	@Override
	public List<String> getBasePackages() {
		return List.of(basePackage);
	}
}
