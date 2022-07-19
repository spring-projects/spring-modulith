/*
 * Copyright 2020-2022 the original author or authors.
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

import lombok.RequiredArgsConstructor;

import java.util.Optional;

import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * A {@link DocumentationSource} that replaces {@literal {@code …}} or {@literal {@link …}} blocks into inline code
 * references
 *
 * @author Oliver Drotbohm
 */
@RequiredArgsConstructor
class CodeReplacingDocumentationSource implements DocumentationSource {

	private final DocumentationSource delegate;
	private final Asciidoctor codeSource;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.docs.DocumentationSource#getDocumentation(com.tngtech.archunit.core.domain.JavaMethod)
	 */
	@Override
	public Optional<String> getDocumentation(JavaMethod method) {

		return delegate.getDocumentation(method)
				.map(codeSource::toAsciidoctor);
	}
}
