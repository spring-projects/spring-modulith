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
package org.springframework.modulith.docs;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.modulith.docs.SpringModulithDocumentationSource.TypeMetadataReader;
import org.springframework.modulith.docs.metadata.TypeMetadata;

/**
 * Unit tests for {@link SpringModulithDocumentationSource.TypeMetadataReader}.
 *
 * @author Oliver Drotbohm
 */
class SpringModulithDocumentationSourceUnitTests {

	@Test // GH-1762
	void parsesCommentContainingEscapedQuoteFollowedByComma() {

		var json = """
				[{"name":"x","comment":"\\",.","methods":[]}]
				""";

		var metadata = TypeMetadataReader.read(new ByteArrayResource(json.getBytes()));

		assertThat(metadata).extracting(TypeMetadata::comment).containsExactly("\",.");
	}

	@Test // GH-1762
	void parsesCommentContainingUnbalancedCurlyBrace() {

		var json = """
				[{"name":"x","comment":"abre { e nao fecha"},{"name":"y","comment":"x"}]
				""";

		var metadata = TypeMetadataReader.read(new ByteArrayResource(json.getBytes()));

		assertThat(metadata).extracting(TypeMetadata::comment).containsExactly("abre { e nao fecha", "x");
	}
}
