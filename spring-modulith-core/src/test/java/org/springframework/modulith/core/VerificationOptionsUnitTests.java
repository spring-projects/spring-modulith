/*
 * Copyright 2025 the original author or authors.
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
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.Types.JMoleculesTypes;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Unit tests for {@link VerificationOptions}.
 *
 * @author Oliver Drotbohm
 */
class VerificationOptionsUnitTests {

	@Test // GH-1185
	void usesJMoleculesVerificationsByDefault() {

		var options = VerificationOptions.defaults();

		assertThat(options.getAdditionalVerifications()).isEqualTo(JMoleculesTypes.getRules());
	}

	@Test // GH-1185
	void addsVerification() {

		var archRule = mock(ArchRule.class);
		var options = VerificationOptions.defaults();

		assertThat(options.andAdditionalVerifications(archRule).getAdditionalVerifications())
				.hasSize(options.getAdditionalVerifications().size() + 1)
				.containsAll(JMoleculesTypes.getRules())
				.contains(archRule);
	}

	@Test // GH-1185
	void replacesVerification() {

		var archRule = mock(ArchRule.class);
		var options = VerificationOptions.defaults().withAdditionalVerifications(archRule);

		assertThat(options.getAdditionalVerifications())
				.hasSize(1)
				.containsExactly(archRule);
	}
}
