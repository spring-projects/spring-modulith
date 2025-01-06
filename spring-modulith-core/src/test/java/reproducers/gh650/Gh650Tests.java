/*
 * Copyright 2024-2025 the original author or authors.
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
package reproducers.gh650;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.NamedInterface;
import org.springframework.modulith.core.TestUtils;

/**
 * @author Oliver Drotbohm
 */
public class Gh650Tests {

	@Test // GH-650
	void usesBasePackageNamedInterface() {

		var module = TestUtils.of("reproducers.gh650").getModuleByName("first").orElseThrow();

		assertThat(module.getNamedInterfaces())
				.extracting(NamedInterface::getName)
				.containsExactlyInAnyOrder("<<UNNAMED>>", "persistence", "persistence.dto");
	}
}
