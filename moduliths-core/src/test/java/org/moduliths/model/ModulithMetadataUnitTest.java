/*
 * Copyright 2019 the original author or authors.
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
package org.moduliths.model;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.moduliths.Modulith;
import org.moduliths.Modulithic;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Unit tests for {@link ModulithMetadata}.
 *
 * @author Oliver Drotbohm
 */
class ModulithMetadataUnitTest {

	@Test
	public void inspectsModulithAnnotation() throws Exception {

		Stream.of(ModulithAnnotated.class, ModuliticAnnotated.class) //
				.map(ModulithMetadata::of) //
				.forEach(it -> {

					assertThat(it.getAdditionalPackages()).containsExactly("com.acme.foo");
					assertThat(it.getSharedModuleNames()).containsExactly("shared.module");
					assertThat(it.getSystemName()).hasValue("systemName");
					assertThat(it.useFullyQualifiedModuleNames()).isTrue();
				});
	}

	@Test
	public void usesDefaultsIfModulithAnnotationsAreMissing() {

		ModulithMetadata metadata = ModulithMetadata.of(SpringBootApplicationAnnotated.class);

		assertThat(metadata.getAdditionalPackages()).isEmpty();
		assertThat(metadata.getSharedModuleNames()).isEmpty();
		assertThat(metadata.getSystemName()).isEmpty();
		assertThat(metadata.useFullyQualifiedModuleNames()).isFalse();
	}

	@Test
	public void rejectsTypeNotAnnotatedWithEitherModulithAnnotationOrSpringBootApplication() {

		assertThatExceptionOfType(IllegalArgumentException.class) //
				.isThrownBy(() -> ModulithMetadata.of(Unannotated.class)) //
				.withMessageContaining(Modulith.class.getSimpleName()) //
				.withMessageContaining(Modulithic.class.getSimpleName()) //
				.withMessageContaining(SpringBootApplication.class.getSimpleName());
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
}
