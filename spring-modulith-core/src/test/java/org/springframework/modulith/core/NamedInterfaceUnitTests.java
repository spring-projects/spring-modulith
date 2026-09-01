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
package org.springframework.modulith.core;

import static org.assertj.core.api.Assertions.*;

import example.Example;
import example.ni.RootType;
import example.ni.api.ApiType;
import example.ni.spi.SpiType;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NamedInterface}.
 *
 * @author Oliver Drotbohm
 */
class NamedInterfaceUnitTests {

	@Test // GH-1839
	void createsNamedInterfaceFromClassesAndFilter() {

		var classes = TestUtils.getClasses(Example.class);
		var result = NamedInterface.of("api", classes, it -> it.getSimpleName().equals("ApiType"));

		assertThat(result.getName()).isEqualTo("api");
		assertThat(result.contains(ApiType.class)).isTrue();
		assertThat(result.contains(SpiType.class)).isFalse();
	}

	@Test // GH-1839
	void createsNamedInterfaceFromJavaPackageAndFilter() {

		var javaPackage = TestUtils.getPackage(RootType.class);
		var result = NamedInterface.of("spi", javaPackage, it -> it.getSimpleName().equals("SpiType"));

		assertThat(result.getName()).isEqualTo("spi");
		assertThat(result.contains(SpiType.class)).isTrue();
		assertThat(result.contains(ApiType.class)).isFalse();
	}
}
