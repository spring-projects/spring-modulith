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
package org.springframework.modulith.events.support;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link CompletionTiming}.
 *
 * @author Oliver Drotbohm
 */
class CompletionTimingUnitTests {

	@Test // GH-1961
	void defaultsToInListener() {
		assertThat(CompletionTiming.from(new MockEnvironment())).isEqualTo(CompletionTiming.IN_LISTENER);
	}

	@Test // GH-1961
	void resolvesRelaxedValuesWithBootConversionService() {

		for (var value : new String[] { "in-interceptor", "in_interceptor", "IN_INTERCEPTOR" }) {

			var environment = new MockEnvironment().withProperty(CompletionTiming.PROPERTY, value);
			environment.setConversionService(new ApplicationConversionService());

			assertThat(CompletionTiming.from(environment)).isEqualTo(CompletionTiming.IN_INTERCEPTOR);
		}
	}

	@Test // GH-1961
	void rejectsUnknownValue() {

		var environment = new MockEnvironment().withProperty(CompletionTiming.PROPERTY, "foo");

		assertThatException().isThrownBy(() -> CompletionTiming.from(environment));
	}
}
