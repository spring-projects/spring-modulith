/*
 * Copyright 2024-2025 the original author or authors.
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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import org.springframework.modulith.core.JavaPackage;

import java.util.Optional;

/**
 * A no-op {@link DocumentationSource} that returns empty {@link Optional}s for all documentation lookups. Used as a
 * fallback when no documentation source is available.
 */
class NoOpDocumentationSource implements DocumentationSource {

	@Override
	public Optional<String> getDocumentation(JavaMethod method) {
		return Optional.empty();
	}

	@Override
	public Optional<String> getDocumentation(JavaClass type) {
		return Optional.empty();
	}

	@Override
	public Optional<String> getDocumentation(JavaPackage pkg) {
		return Optional.empty();
	}
}
