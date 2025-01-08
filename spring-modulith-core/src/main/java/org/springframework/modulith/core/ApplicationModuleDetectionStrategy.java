/*
 * Copyright 2020-2025 the original author or authors.
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

import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.Types.JMoleculesTypes;

/**
 * Strategy interface to customize which packages are considered module base packages.
 *
 * @author Oliver Drotbohm
 */
public interface ApplicationModuleDetectionStrategy {

	/**
	 * Given the {@link JavaPackage} that Spring Modulith was initialized with, return the base packages that are supposed
	 * to be considered base packages for {@link ApplicationModule}s.
	 *
	 * @param rootPackage will never be {@literal null}.
	 * @return must not be {@literal null}.
	 */
	Stream<JavaPackage> getModuleBasePackages(JavaPackage rootPackage);

	/**
	 * Optionally customize the detection of {@link NamedInterfaces} for a module with the given base package and the
	 * pre-obtained {@link ApplicationModuleInformation}. Defaults to
	 * {@link NamedInterfaces#of(JavaPackage, ApplicationModuleInformation)}. {@link NamedInterfaces} exposes a
	 * {@link NamedInterfaces.Builder} API to define the selection of packages to be considered named interfaces.
	 *
	 * @param basePackage will never be {@literal null}.
	 * @param information will never be {@literal null}.
	 * @return must not be {@literal null}.
	 * @see NamedInterfaces#of(JavaPackage, ApplicationModuleInformation)
	 * @see NamedInterfaces#builder(JavaPackage)
	 * @since 1.4
	 */
	default NamedInterfaces detectNamedInterfaces(JavaPackage basePackage, ApplicationModuleInformation information) {
		return NamedInterfaces.of(basePackage, information);
	}

	/**
	 * A {@link ApplicationModuleDetectionStrategy} that considers all direct sub-packages of the Moduliths base package
	 * to be module base packages.
	 *
	 * @return will never be {@literal null}.
	 */
	static ApplicationModuleDetectionStrategy directSubPackage() {
		return pkg -> pkg.getDirectSubPackages().stream();
	}

	/**
	 * A {@link ApplicationModuleDetectionStrategy} that considers packages explicitly annotated with
	 * {@link ApplicationModule} module base packages.
	 *
	 * @return will never be {@literal null}.
	 * @deprecated since 1.3. Use {@link #explicitlyAnnotated()} instead.
	 */
	@Deprecated(forRemoval = true)
	static ApplicationModuleDetectionStrategy explictlyAnnotated() {
		return explicitlyAnnotated();
	}

	/**
	 * A {@link ApplicationModuleDetectionStrategy} that considers packages explicitly annotated with
	 * {@link ApplicationModule} module base packages.
	 *
	 * @return will never be {@literal null}.
	 */
	static ApplicationModuleDetectionStrategy explicitlyAnnotated() {
		return pkg -> Stream.of(ApplicationModule.class, JMoleculesTypes.getModuleAnnotationTypeIfPresent())
				.filter(Objects::nonNull)
				.flatMap(pkg::getSubPackagesAnnotatedWith);
	}
}
