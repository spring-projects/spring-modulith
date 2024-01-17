/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.modulith.core.util;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.acme.myproject.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Unit tests for {@link ApplicationModulesExporter}.
 *
 * @author Oliver Drotbohm
 */
class ApplicationModulesExporterUnitTests {

	private static final ApplicationModulesExporter EXPORTER = new ApplicationModulesExporter(
			ApplicationModules.of(Application.class));
	private static final JsonPath NAMED_INTERFACES = JsonPath.compile("$..namedInterfaces");

	@Test // #119
	void rendersApplicationModulesAsJson() {

		assertThatNoException().isThrownBy(() -> {
			new ObjectMapper().readTree(EXPORTER.toJson());
		});
	}

	@Test // #227
	void simpleRenderingDoesNotListNamedInterfaces() {
		assertThat((List<?>) NAMED_INTERFACES.read(EXPORTER.toJson())).isEmpty();
	}

	@Test // #227
	void fullRenderingListsNamedInterfaces() {
		assertThat((List<?>) NAMED_INTERFACES.read(EXPORTER.toFullJson())).isNotEmpty();
	}

	@Test // #227
	void fullRenderingProducesValidJson() {
		assertThatNoException().isThrownBy(() -> {
			new ObjectMapper().readTree(EXPORTER.toJson());
		});
	}
}
