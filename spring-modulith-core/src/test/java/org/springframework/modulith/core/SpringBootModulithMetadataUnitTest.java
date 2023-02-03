/*
 * Copyright 2023 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Unit tests for {@link SpringBootModulithMetadata}.
 *
 * @author Oliver Drotbohm
 */
class SpringBootModulithMetadataUnitTest {

	@Test // #130
	void usesClassNameAsSystemNameDefault() {

		var metadata = SpringBootModulithMetadata.of(SpringBootApp.class);

		assertThat(metadata).hasValueSatisfying(it -> {
			assertThat(it.getSystemName()).hasValue(SpringBootApp.class.getSimpleName());
		});
	}

	@Test // #130
	void doesNotExposeASystemNameForPackageBasedMetadata() {
		assertThat(SpringBootModulithMetadata.of("com.acme").getSystemName()).isEmpty();
	}

	@SpringBootApplication
	private static class SpringBootApp {}
}
