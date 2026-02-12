/*
 * Copyright 2020-2026 the original author or authors.
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

/**
 * Abstraction for low-level module information. Used to support different annotations to configure metadata about a
 * module.
 *
 * @author Oliver Drotbohm
 * @since 1.4, previously package private.
 */
public interface ApplicationModuleInformation {

	/**
	 * Creates a new {@link ApplicationModuleInformation} for the given {@link JavaPackage}.
	 *
	 * @param javaPackage must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static ApplicationModuleInformation of(JavaPackage javaPackage) {
		return ApplicationModuleInformationFactory.of(javaPackage);
	}

	/**
	 * Returns the display name to be used to describe the module.
	 *
	 * @return will never be {@literal null}.
	 */
	default Optional<String> getDisplayName() {
		return Optional.empty();
	}

	/**
	 * Returns all allowed dependencies.
	 *
	 * @return will never be {@literal null}.
	 */
	List<String> getDeclaredDependencies();

	/**
	 * Returns whether the module is considered open.
	 *
	 * @see org.springframework.modulith.ApplicationModule.Type
	 * @since 1.2
	 */
	boolean isOpen();
}
