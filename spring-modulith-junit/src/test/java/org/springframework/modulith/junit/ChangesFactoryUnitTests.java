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
package org.springframework.modulith.junit;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Unit tests for {@link ChangesFactory}.
 *
 * @author Oliver Drotbohm
 * @author Valentin Bossi
 */
class ChangesFactoryUnitTests {

	@Test // GH-31
	void returnsNoChangesIfDisabledByProperty() {

		var environment = getEnvironment(Map.of("spring.modulith.test.skip-optimizations", "true"));

		assertThat(ChangesFactory.getChanges(environment)).isEqualTo(Changes.NONE);
		assertThat(ChangesFactory.getChanges(environment).skipTestsOnNoChanges()).isFalse();
	}

	@Test // GH-1438
	void skipTestsOnNoChangesSetByProperty() {

		var environment = getEnvironment(Map.of("spring.modulith.test.on-no-changes", "skip-all"));

		assertThat(ChangesFactory.getChanges(environment).skipTestsOnNoChanges()).isTrue();
	}

	@Test // GH-1438
	void executeTestsOnNoChangesSetByProperty() {

		var environment = getEnvironment(Map.of("spring.modulith.test.on-no-changes", "execute-all"));

		assertThat(ChangesFactory.getChanges(environment).skipTestsOnNoChanges()).isFalse();
	}

	@Test // GH-1438
	void executeTestsByDefaultOrInvalidValue() {

		var environment = getEnvironment(Collections.emptyMap());

		assertThat(ChangesFactory.getChanges(environment).skipTestsOnNoChanges()).isFalse();
	}

	@Test // GH-1438
	void rejectsInvalidOnNoChangesConfigurationValue() {

		var environment = getEnvironment(Map.of("spring.modulith.test.on-no-changes", "1"));

		assertThatIllegalArgumentException().isThrownBy(() -> {
			ChangesFactory.getChanges(environment);
		});
	}

	private static Environment getEnvironment(Map<String, Object> properties) {

		var propertySource = new MapPropertySource("properties", properties);

		var environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(propertySource);

		return environment;
	}
}
