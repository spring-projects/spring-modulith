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

import com.tngtech.archunit.lang.ArchRule;

/**
 * A jMolecules architecture verification executed for {@link ApplicationModules}. Use the implementations for the
 * individual architectural styles ({@link HexagonalVerifications}, {@link OnionVerifications} and
 * {@link LayeredVerifications}) to customize the verifications executed by default and register them via
 * {@link VerificationOptions#withArchitectureVerifications(JMoleculesArchitectureVerifications)}. The implementations
 * require the jMolecules ArchUnit integration on the classpath.
 *
 * @author Oliver Drotbohm
 * @since 2.2
 */
public interface TechnicalArchitectureVerifications {

	/**
	 * Creates the {@link ArchRule} to verify the given {@link ApplicationModules}.
	 *
	 * @param modules will never be {@literal null}.
	 * @return must not be {@literal null}.
	 */
	ArchRule createRule(ApplicationModules modules);
}
