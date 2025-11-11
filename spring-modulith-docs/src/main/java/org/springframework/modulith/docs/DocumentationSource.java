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
package org.springframework.modulith.docs;

import java.util.Optional;

import org.springframework.modulith.core.JavaPackage;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * Interface to abstract different ways of looking up documentation for code abstractions.
 *
 * @author Oliver Drotbohm
 */
public interface DocumentationSource {

	/**
	 * Returns the documentation to be used for the given {@link JavaMethod}.
	 *
	 * @param method must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Optional<String> getDocumentation(JavaMethod method);

	/**
	 * Returns the documentation to be used for the given {@link JavaClass}.
	 *
	 * @param type must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 1.3
	 */
	Optional<String> getDocumentation(JavaClass type);

	/**
	 * Returns the documentation for the given {@link JavaPackage}.
	 *
	 * @param pkg must not be {@literal null}.
	 * @return will never be {@literal null}.
	 * @since 2.0
	 */
	Optional<String> getDocumentation(JavaPackage pkg);
}
