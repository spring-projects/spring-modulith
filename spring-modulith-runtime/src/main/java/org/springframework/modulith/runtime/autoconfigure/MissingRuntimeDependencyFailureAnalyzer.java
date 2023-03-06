/*
 * Copyright 2023 the original author or authors.
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

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

/**
 * {@link FailureAnalyzer} for {@link MissingRuntimeDependencyException}.
 *
 * @author Michael Weirauch
 */
class MissingRuntimeDependencyFailureAnalyzer extends AbstractFailureAnalyzer<MissingRuntimeDependencyException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, MissingRuntimeDependencyException cause) {
		return new FailureAnalysis(
				String.format("Spring Modulith requires the dependency '%s' to be on the runtime classpath.",
						cause.getDependencyName()),
				"Add the missing dependency to the runtime classpath of your project.", cause);
	}
}
