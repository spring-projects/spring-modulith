/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.modulith.model;

import java.util.stream.Stream;

import org.springframework.modulith.Module;

/**
 * Strategy interface to customize which packages are considered module base packages.
 *
 * @author Oliver Drotbohm
 */
public interface ModuleDetectionStrategy {

	/**
	 * Given the {@link JavaPackage} that Moduliths was initialized with, return the base packages for all modules in the
	 * system.
	 *
	 * @param basePackage will never be {@literal null}.
	 * @return must not be {@literal null}.
	 */
	Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage);

	/**
	 * A {@link ModuleDetectionStrategy} that considers all direct sub-packages of the Moduliths base package to be module
	 * base packages.
	 *
	 * @return will never be {@literal null}.
	 */
	static ModuleDetectionStrategy directSubPackage() {
		return ModuleDetectionStrategies.DIRECT_SUB_PACKAGES;
	}

	/**
	 * A {@link ModuleDetectionStrategy} that considers packages explicitly annotated with {@link Module} module base
	 * packages.
	 *
	 * @return will never be {@literal null}.
	 */
	static ModuleDetectionStrategy explictlyAnnotated() {
		return ModuleDetectionStrategies.EXPLICITLY_ANNOTATED;
	}
}
