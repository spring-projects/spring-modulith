/*
 * Copyright 2020-2025 the original author or authors.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.Modulithic;

/**
 * Unit tests for {@link AnnotationModulithMetadata}.
 *
 * @author Oliver Drotbohm
 */
class AnnotationModulithMetadataUnitTest {

	@Test
	void findsCustomizationsOnClass() {

		assertThat(AnnotationModulithMetadata.of(Sample.class)).hasValueSatisfying(it -> {
			assertThat(it.useFullyQualifiedModuleNames()).isTrue();
		});
	}

	@Test
	void findsCustomizationsOnClassForMetaAnnotationUsage() {

		assertThat(AnnotationModulithMetadata.of(MetaSample.class)).hasValueSatisfying(it -> {
			assertThat(it.useFullyQualifiedModuleNames()).isTrue();
		});
	}

	@Test // #130
	void usesSimpleClassNameAsDefaultSystemName() {

		assertThat(AnnotationModulithMetadata.of(Sample.class)).hasValueSatisfying(it -> {
			assertThat(it.getSystemName()).hasValue(Sample.class.getSimpleName());
		});
	}

	@Test // GH-1247
	void skipsNestedAdditionalPackages() {

		assertThat(AnnotationModulithMetadata.of(WithInvalidAdditionalpackages.class)).hasValueSatisfying(it -> {
			assertThat(it.getBasePackages())
					.hasSize(1)
					.contains(WithInvalidAdditionalpackages.class.getPackageName());
		});
	}

	@Modulithic(useFullyQualifiedModuleNames = true)
	static class Sample {}

	@Intermediate
	static class MetaSample {}

	@Retention(RetentionPolicy.RUNTIME)
	@Modulithic(useFullyQualifiedModuleNames = true)
	@interface Intermediate {}

	@Modulithic(additionalPackages = "org.springframework.modulith.core.nested")
	static class WithInvalidAdditionalpackages {}
}
