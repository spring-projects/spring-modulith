/*
 * Copyright 2026 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

/**
 * Verifies the default verifications and their customization work without the jMolecules ArchUnit integration on the
 * classpath. Only executes in the dedicated Surefire execution excluding the dependency.
 *
 * @author Oliver Drotbohm
 */
class VerificationOptionsWithoutJMoleculesArchUnitTests {

	@Test // GH-193
	void runsWithoutJMoleculesArchUnitIntegration() {

		assumeFalse(ClassUtils.isPresent("org.jmolecules.archunit.JMoleculesArchitectureRules",
				getClass().getClassLoader()));

		var modules = TestUtils.of("example", "example.ninvalid", "example.cycle..");

		var options = VerificationOptions.defaults();

		assertThat(options.getAdditionalVerifications(modules)).isEmpty();

		assertThatNoException().isThrownBy(() -> modules.detectViolations(options));
	}
}
