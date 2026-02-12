/*
 * Copyright 2022-2026 the original author or authors.
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
package org.springframework.modulith.moments;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.modulith.moments.Quarter.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Unit tests for {@link Quarter}.
 *
 * @author Oliver Drotbohm
 */
class QuarterUnitTests {

	@TestFactory
	Stream<DynamicTest> calculatesNextQuarterCorrectly() {

		Map<Quarter, Quarter> mappings = new HashMap<>();
		mappings.put(Q1, Q2);
		mappings.put(Q2, Q3);
		mappings.put(Q3, Q4);
		mappings.put(Q4, Q1);

		return DynamicTest.stream(mappings.entrySet().iterator(),
				it -> String.format("%s follows %s", it.getValue(), it.getKey()),
				it -> assertThat(it.getKey().next()).isEqualTo(it.getValue()));
	}
}
