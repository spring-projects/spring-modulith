/*
 * Copyright 2023-2026 the original author or authors.
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
package org.springframework.modulith.runtime.autoconfigure;

/**
 * An Exception carrying information about a missing runtime dependency to be analyzed by
 * {@link MissingRuntimeDependencyFailureAnalyzer}.
 *
 * @author Michael Weirauch
 * @author Oliver Drotbohm
 */
class MissingRuntimeDependency extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String description, suggestedAction;

	MissingRuntimeDependency(String description, String suggestedAction) {

		this.description = description;
		this.suggestedAction = suggestedAction;
	}

	String getDescription() {
		return description;
	}

	String getSuggestedAction() {
		return suggestedAction;
	}
}
