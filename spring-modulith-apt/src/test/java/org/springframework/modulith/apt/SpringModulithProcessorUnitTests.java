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
package org.springframework.modulith.apt;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.modulith.apt.SpringModulithProcessor.*;

import io.toolisticon.cute.Cute;
import io.toolisticon.cute.CuteApi.BlackBoxTestInterface;
import io.toolisticon.cute.CuteApi.BlackBoxTestSourceFilesAndProcessorInterface;
import io.toolisticon.cute.CuteApi.DoCustomAssertions;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/**
 * Unit tests for {@link SpringModulithProcessor}.
 *
 * @author Oliver Drotbohm
 */
class SpringModulithProcessorUnitTests {

	BlackBoxTestSourceFilesAndProcessorInterface baseBlackBoxSetup = Cute.blackBoxTest()
			.given()
			.processor(SpringModulithProcessor.class);

	@Test // GH-854
	void extractsJavadoc() throws Exception {

		assertSucceded(getSourceFile("SampleComponent"));

		var output = new File(JSON_LOCATION);

		assertThat(output).exists();
	}

	@Test // GH-854
	void stripsNewLinesFromComments() throws Exception {

		assertSucceded(getSourceFile("SampleComponent"));

		var output = new File(JSON_LOCATION);

		var context = JsonPath.parse(output);
		var comment = context.read("$[?(@.name == 'example.SampleComponent')].comment", String[].class)[0];

		assertThat(comment).isNotBlank().doesNotContain("\n");
	}

	@Test // GH-962
	void extractsJavadocFromMethodWithClassNestedInInterfaceAsParameter() {

		assertThatNoException().isThrownBy(() -> {
			assertSucceded(getSourceFile("SampleInterface"));
		});
	}

	private static DoCustomAssertions assertSucceded(String source) {

		return assertSourceProcessed(source)
				.thenExpectThat().compilationSucceeds()
				.executeTest();
	}

	private static BlackBoxTestInterface assertSourceProcessed(String source) {

		return Cute.blackBoxTest()
				.given()
				.processor(SpringModulithProcessor.class)
				.andSourceFiles(source)
				.whenCompiled();
	}

	private static String getSourceFile(String name) {
		return "/example/" + name + ".java";
	}
}
