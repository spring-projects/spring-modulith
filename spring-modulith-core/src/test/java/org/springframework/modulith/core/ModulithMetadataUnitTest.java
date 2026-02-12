/*
 * Copyright 2019-2026 the original author or authors.
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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;
import org.springframework.modulith.Modulithic;

/**
 * Unit tests for {@link ModulithMetadata}.
 *
 * @author Oliver Drotbohm
 */
class ModulithMetadataUnitTest {

	@Test
	void inspectsModulithAnnotation() throws Exception {

		Stream.of(ModulithAnnotated.class, ModuliticAnnotated.class) //
				.map(ModulithMetadata::of) //
				.forEach(it -> {

					assertThat(it.getBasePackages()).containsExactly("org.springframework.modulith.core", "com.acme.foo");
					assertThat(it.getSharedModuleIdentifiers())
							.extracting(ApplicationModuleIdentifier::toString)
							.containsExactly("shared.module");
					assertThat(it.getSystemName()).hasValue("systemName");
					assertThat(it.useFullyQualifiedModuleNames()).isTrue();
				});
	}

	@Test
	void usesDefaultsIfModulithAnnotationsAreMissing() {

		ModulithMetadata metadata = ModulithMetadata.of(SpringBootApplicationAnnotated.class);

		assertThat(metadata.getBasePackages()).contains("org.springframework.modulith.core");
		assertThat(metadata.getSharedModuleIdentifiers()).isEmpty();
		assertThat(metadata.getSystemName()).hasValue(SpringBootApplicationAnnotated.class.getSimpleName());
		assertThat(metadata.useFullyQualifiedModuleNames()).isFalse();
	}

	@Test
	void rejectsTypeNotAnnotatedWithEitherModulithAnnotationOrSpringBootApplication() {

		assertThatExceptionOfType(IllegalArgumentException.class) //
				.isThrownBy(() -> ModulithMetadata.of(Unannotated.class)) //
				.withMessageContaining(Unannotated.class.getSimpleName()) //
				.withMessageContaining(Modulith.class.getSimpleName()) //
				.withMessageContaining(Modulithic.class.getSimpleName()) //
				.withMessageContaining(SpringBootApplication.class.getSimpleName());
	}

	@Test // GH-797
	void returnsUniqueBasePackages() {

		var metadata = ModulithMetadata.of(AdditionalPackagesShadowing.class);

		assertThat(metadata.getBasePackages()).containsExactly(getClass().getPackageName());
	}

	@Test // GH-1015
	void considersModulithicOnPackageLookup() {

		var metadata = ModulithMetadata.of("empty");

		assertThat(metadata.getSystemName()).hasValue("customSystemName");
	}

	@Modulith(additionalPackages = "com.acme.foo", //
			sharedModules = "shared.module", //
			systemName = "systemName", //
			useFullyQualifiedModuleNames = true)
	static class ModulithAnnotated {}

	@Modulithic(additionalPackages = "com.acme.foo", //
			sharedModules = "shared.module", //
			systemName = "systemName", //
			useFullyQualifiedModuleNames = true)
	static class ModuliticAnnotated {}

	@SpringBootApplication
	static class SpringBootApplicationAnnotated {}

	static class Unannotated {}

	@Modulithic(additionalPackages = "org.springframework.modulith.core")
	static class AdditionalPackagesShadowing {}
}
