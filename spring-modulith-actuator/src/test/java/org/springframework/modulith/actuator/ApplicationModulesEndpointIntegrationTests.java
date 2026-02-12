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
package org.springframework.modulith.actuator;

import static org.assertj.core.api.Assertions.*;

import net.minidev.json.JSONArray;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.TestApplicationModules;

import com.jayway.jsonpath.JsonPath;

/**
 * Integration tests for {@link ApplicationModulesEndpoint}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModulesEndpointIntegrationTests {

	@Test
	void exposesApplicationModulesAsMap() throws Exception {

		var modules = TestApplicationModules.of("example");
		var endpoint = ApplicationModulesEndpoint.ofApplicationModules(() -> modules);
		var result = endpoint.getApplicationModules();
		var context = JsonPath.parse(result);

		assertThat(context.<String> read("$.a.basePackage")).isEqualTo("example.a");
		assertThat(context.<JSONArray> read("$.a.dependencies")).isEmpty();

		assertThat(context.<String> read("$.b.basePackage")).isEqualTo("example.b");
		assertThat(context.<String> read("$.b.dependencies[0].target")).isEqualTo("a");
		assertThat(context.<JSONArray> read("$.b.dependencies[0].types"))
				.containsExactlyInAnyOrder("EVENT_LISTENER", "USES_COMPONENT");
	}
}
