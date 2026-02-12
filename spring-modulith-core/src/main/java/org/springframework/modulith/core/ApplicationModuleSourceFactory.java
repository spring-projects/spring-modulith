/*
 * Copyright 2024-2026 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * SPI to allow build units contribute additional {@link ApplicationModuleSource}s in the form of either declaring them
 * directly via {@link #getModuleBasePackages()} and {@link #getApplicationModuleSources(Function, boolean)} or via
 * provided {@link #getRootPackages()} and subsequent resolution via
 * {@link #getApplicationModuleSources(JavaPackage, ApplicationModuleDetectionStrategy, boolean)} for each of the
 * packages provided. <br>
 * The following snippet would register {@link ApplicationModuleSource}s for {@code com.acme.foo} and
 * {@code com.acme.bar} directly:
 *
 * <pre>
 * {@code
 * class MyCustomFactory implements ApplicationModuleSourceFactory {
 *
 * 	&#64;Override
 * 	public List<String> getModuleBasePackages() {
 * 		return List.of("com.acme.foo", "com.acme.bar");
 * 	}
 * }
 * }
 * </pre>
 *
 * The following snippet would register all modules located underneath {@code com.acme} found via the
 * {@link ApplicationModuleDetectionStrategy#explicitlyAnnotated()} strategy:
 *
 * <pre>
 * {@code
 * class MyCustomFactory implements ApplicationModuleSourceFactory {
 *
 * 	&#64;Override
 * 	public List<String> getRootPackages() {
 * 		return List.of("com.acme");
 * 	}
 *
 * 	&#64;Override
 * 	ApplicationModuleDetectionStrategy getApplicationModuleDetectionStrategy() {
 * 		return ApplicationModuleDetectionStrategy.explicitlyAnnotated();
 * 	}
 * }
 * }
 * </pre>
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
public interface ApplicationModuleSourceFactory {

	/**
	 * Returns the additional root packages to be considered. The ones returned from this method will be scanned for
	 * {@link ApplicationModuleSource}s via
	 * {@link #getApplicationModuleSources(JavaPackage, ApplicationModuleDetectionStrategy, boolean)} using the
	 * {@link ApplicationModuleDetectionStrategy} returned from {@link #getApplicationModuleDetectionStrategy()}. If the
	 * latter is {@literal null}, the default {@link ApplicationModuleDetectionStrategy} is used.
	 *
	 * @return must not be {@literal null}.
	 */
	default List<String> getRootPackages() {
		return Collections.emptyList();
	}

	/**
	 * Returns additional module base packages to create {@link ApplicationModuleSource}s from. Subsequently handled by
	 * {@link #getApplicationModuleSources(Function, boolean)}.
	 *
	 * @return must not be {@literal null}.
	 */
	default List<String> getModuleBasePackages() {
		return Collections.emptyList();
	}

	/**
	 * Returns the {@link ApplicationModuleDetectionStrategy} to be used to detect {@link ApplicationModuleSource}s from
	 * the packages returned by {@link #getRootPackages()}. If {@literal null} is returned, the default
	 * {@link ApplicationModuleDetectionStrategy} will be used.
	 *
	 * @return can be {@literal null}.
	 */
	@Nullable
	default ApplicationModuleDetectionStrategy getApplicationModuleDetectionStrategy() {
		return null;
	}

	/**
	 * Creates all {@link ApplicationModuleSource}s using the given base package and
	 * {@link ApplicationModuleDetectionStrategy}.
	 *
	 * @param rootPackage will never be {@literal null}.
	 * @param strategy will never be {@literal null}.
	 * @param useFullyQualifiedModuleNames whether to use fully-qualified names for application modules.
	 * @return must not be {@literal null}.
	 * @see ApplicationModuleSource#from(JavaPackage, ApplicationModuleDetectionStrategy, boolean)
	 */
	default Stream<ApplicationModuleSource> getApplicationModuleSources(JavaPackage rootPackage,
			ApplicationModuleDetectionStrategy strategy, boolean useFullyQualifiedModuleNames) {

		return ApplicationModuleSource.from(rootPackage, strategy, useFullyQualifiedModuleNames);
	}

	/**
	 * Creates {@link ApplicationModuleSource} for individually, manually described application modules.
	 *
	 * @param packages will never be {@literal null}.
	 * @param useFullyQualifiedModuleNames whether to use fully-qualified names for application modules.
	 * @return must not be {@literal null}.
	 * @see ApplicationModuleSource#from(JavaPackage, String)
	 */
	default Stream<ApplicationModuleSource> getApplicationModuleSources(Function<String, JavaPackage> packages,
			boolean useFullyQualifiedModuleNames) {

		return getModuleBasePackages().stream()
				.map(packages)
				.map(it -> ApplicationModuleSource.from(it, useFullyQualifiedModuleNames ? it.getName() : it.getLocalName()));
	}
}
