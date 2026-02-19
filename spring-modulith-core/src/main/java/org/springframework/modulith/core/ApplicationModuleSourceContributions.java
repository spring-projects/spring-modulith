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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.core.io.support.SpringFactoriesLoader;

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
	private final List<? extends ApplicationModuleSourceFactory> factories;
	private final ApplicationModuleDetectionStrategy defaultStrategy;
	private final boolean useFullyQualifiedModuleNames;

	/**
	 * Creates a new {@link ApplicationModuleSourceContributions} for the given default
	 * {@link ApplicationModuleDetectionStrategy} and whether to use fully-qualified module names.
	 *
	 * @param defaultStrategy must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames whether to use fully-qualified module names.
	 */
	public static ApplicationModuleSourceContributions of(ApplicationModuleDetectionStrategy defaultStrategy,
			boolean useFullyQualifiedModuleNames) {

		var loader = SpringFactoriesLoader.forResourceLocation(LOCATION, ApplicationModules.class.getClassLoader());

		return new ApplicationModuleSourceContributions(loader.load(ApplicationModuleSourceFactory.class), defaultStrategy,
				useFullyQualifiedModuleNames);
	}

	/**
	 * Creates a new {@link ApplicationModuleSourceContributions} for the given {@link ApplicationModuleSourceFactory}s,
	 * default {@link ApplicationModuleDetectionStrategy} and whether to use fully-qualified module names.
	 *
	 * @param factories must not be {@literal null}.
	 * @param defaultStrategy must not be {@literal null}.
	 * @param useFullyQualifiedModuleNames whether to use fully-qualified module names.
	 */
	ApplicationModuleSourceContributions(List<? extends ApplicationModuleSourceFactory> factories,
			ApplicationModuleDetectionStrategy defaultStrategy, boolean useFullyQualifiedModuleNames) {

		this.rootPackages = factories.stream().flatMap(it -> it.getRootPackages().stream()).toList();
		this.factories = factories;
		this.defaultStrategy = defaultStrategy;
		this.useFullyQualifiedModuleNames = useFullyQualifiedModuleNames;
	}

	/**
	 * @return will never be {@literal null}.
	 */
	public List<String> getRootPackages() {
		return rootPackages;
	}

	/**
	 * @return will never be {@literal null}.
	 */
	public Stream<ApplicationModuleSource> getSources(Classes classes) {

		Function<String, JavaPackage> packageFactory = it -> JavaPackage.of(classes, it);

		return factories.stream().flatMap(factory -> {

			var contributedPackages = factory.getRootPackages();
			var factoryStrategy = factory.getApplicationModuleDetectionStrategy();
			var strategy = factoryStrategy == null ? defaultStrategy : factoryStrategy;

			// Add discovered ApplicationModuleSources

			var result = new ArrayList<ApplicationModuleSource>();

			contributedPackages.stream()
					.map(packageFactory)
					.flatMap(it -> factory.getApplicationModuleSources(it, strategy, useFullyQualifiedModuleNames))
					.forEach(result::add);

			// Add enumerated ApplicationModuleSources

			factory.getApplicationModuleSources(packageFactory, useFullyQualifiedModuleNames).forEach(result::add);

			return result.stream();
		});
	}
}
