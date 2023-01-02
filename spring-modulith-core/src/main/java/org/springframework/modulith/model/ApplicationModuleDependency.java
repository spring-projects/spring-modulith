/*
 * Copyright 2022-2023 the original author or authors.
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

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * A dependency between two {@link ApplicationModule}s.
 *
 * @author Oliver Drotbohm
 */
public interface ApplicationModuleDependency {

	/**
	 * The source java type establishing the dependency.
	 *
	 * @return will never be {@literal null}.
	 */
	JavaClass getSourceType();

	/**
	 * The dependency target type.
	 *
	 * @return will never be {@literal null}.
	 */
	JavaClass getTargetType();

	/**
	 * The type of the dependency.
	 *
	 * @return will never be {@literal null}.
	 */
	DependencyType getDependencyType();

	/**
	 * The target {@link ApplicationModule}.
	 *
	 * @return will never be {@literal null}.
	 */
	ApplicationModule getTargetModule();
}
