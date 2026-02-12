/*
 * Copyright 2025-2026 the original author or authors.
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
import org.springframework.modulith.core.Violations;

/**
 * A {@link org.springframework.boot.diagnostics.FailureAnalyzer} to give explanation what's wrong when a runtime
 * application module verification fails.
 *
 * @author Oliver Drotbohm
 * @since 2.0
 * @soundtrack Martin Kohlstedt - ZIN (Flur)
 */
class FailedModuleVerificationFailureAnalyzer extends AbstractFailureAnalyzer<Violations> {

	/*
	 * (non-Javadoc)
	 * @see org.springframework.boot.diagnostics.AbstractFailureAnalyzer#analyze(java.lang.Throwable, java.lang.Throwable)
	 */
	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, Violations cause) {

		var description = """
				Spring Modulith application module verification was enabled and failed! The following violations were detected:

				%s
				""".formatted(cause.getMessage());

		var action = """
				Please fix the architectural violations or disable the runtime verification by setting spring.modulith.runtime.verification-enabled to false or removing it entirely.
				""";

		return new FailureAnalysis(description, action, cause);
	}
}
