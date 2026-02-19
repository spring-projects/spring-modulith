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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.core.io.support.SpringFactoriesLoader;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Lookup of external {@link ApplicationModuleSource} contributions via {@link ApplicationModuleSourceFactory}
 * implementations.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
class ApplicationModuleSourceContributions {

	static String LOCATION = SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION;

	private final List<String> rootPackages;
	private final List<ApplicationModuleSource> sources;

	/**
	 * Creates a new {@link ApplicationModuleSourceContributions} for the given importer function, default
	 * {@link ApplicationModuleDetectionStrategy} and whether to use fully-qualified module names.
	 *
	 * @param importer must not be {@literal null}.
	 * @param defaultStrategy must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames whether to use fully-qualified module names.
	 */
	public static ApplicationModuleSourceContributions of(Function<Collection<String>, JavaClasses> importer,
			ApplicationModuleDetectionStrategy defaultStrategy, boolean useFullyQualifiedModuleNames) {

		var loader = SpringFactoriesLoader.forResourceLocation(LOCATION, ApplicationModules.class.getClassLoader());

		return new ApplicationModuleSourceContributions(loader.load(ApplicationModuleSourceFactory.class), importer,
				defaultStrategy, useFullyQualifiedModuleNames);
	}

	static ApplicationModuleSourceContributions of(List<? extends ApplicationModuleSourceFactory> factories,
			Function<Collection<String>, JavaClasses> importer,
			ApplicationModuleDetectionStrategy defaultStrategy, boolean useFullyQualifiedModuleNames) {
		return new ApplicationModuleSourceContributions(factories, importer, defaultStrategy, useFullyQualifiedModuleNames);
	}

	/**
	 * Creates a new {@link ApplicationModuleSourceContributions} for the given {@link ApplicationModuleSourceFactory}s,
	 * importer function, default {@link ApplicationModuleDetectionStrategy} and whether to use fully-qualified module
	 * names.
	 *
	 * @param factories must not be {@literal null}.
	 * @param importer must not be {@literal null}.
	 * @param defaultStrategy must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames whether to use fully-qualified module names.
	 */
	ApplicationModuleSourceContributions(List<? extends ApplicationModuleSourceFactory> factories,
			Function<Collection<String>, JavaClasses> importer,
			ApplicationModuleDetectionStrategy defaultStrategy, boolean useFullyQualifiedModuleNames) {

		this.rootPackages = new ArrayList<>();
		this.sources = new ArrayList<>();

		factories.forEach(factory -> {

			var contributedPackages = factory.getRootPackages();
			var factoryStrategy = factory.getApplicationModuleDetectionStrategy();
			var classes = importer.apply(contributedPackages);
			var strategy = factoryStrategy == null ? defaultStrategy : factoryStrategy;

			// Add discovered ApplicationModuleSources

			rootPackages.addAll(contributedPackages);

			contributedPackages.stream()
					.map(it -> JavaPackage.of(Classes.of(classes), it))
					.flatMap(it -> factory.getApplicationModuleSources(it, strategy, useFullyQualifiedModuleNames))
					.forEach(this.sources::add);

			// Add enumerated ApplicationModuleSources

			Function<String, JavaPackage> packageRegistrar = it -> {
				return JavaPackage.of(Classes.of(importer.apply(List.of(it))), it);
			};

			factory.getApplicationModuleSources(packageRegistrar, useFullyQualifiedModuleNames).forEach(this.sources::add);
		});
	}

	/**
	 * @return will never be {@literal null}.
	 */
	public Stream<String> getRootPackages() {
		return rootPackages.stream();
	}

	/**
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModuleSource> getSources() {
		return sources.stream();
	}
}
