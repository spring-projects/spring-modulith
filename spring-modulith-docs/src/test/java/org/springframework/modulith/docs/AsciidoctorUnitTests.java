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
package org.springframework.modulith.docs;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * @author Oliver Drotbohm
 */
class AsciidoctorUnitTests {

	Asciidoctor asciidoctor = Asciidoctor.withJavadocBase(ApplicationModules.of("org.springframework.modulith"), "{javadoc}");

	@Test
	void formatsInlineCode() {
		assertThat(asciidoctor.toInlineCode("Foo")).isEqualTo("`Foo`");
	}

	@Test
	void rendersLinkToMethodReference() {

		assertThat(asciidoctor.toInlineCode("Documenter#toModuleCanvas(Module, CanvasOptions)"))
				.isEqualTo("link:{javadoc}/org/springframework/modulith/docs/Documenter.html"
						+ "[`o.s.m.d.Documenter#toModuleCanvas(Module, CanvasOptions)`]");
	}

	@Test
	void doesNotRenderLinkToMethodReferenceForNonPublicType() {

		assertThat(asciidoctor.toInlineCode("DocumentationSource#getDocumentation(JavaMethod)"))
				.isEqualTo("`o.s.m.d.DocumentationSource#getDocumentation(JavaMethod)`");
	}

	@Test
	void rendersInlineCodeForNonModuleTypeCorrectly() {

		JavaClass type = new ClassFileImporter().importClass(ApplicationContext.class);

		assertThatCode(() -> asciidoctor.toInlineCode(type)).doesNotThrowAnyException();
	}

	@Test
	void cleansUpJavadocForConfigurationProperties() {

		ConfigurationProperties metadata = new ConfigurationProperties();

		assertThat(metadata).containsExactly(new ConfigurationProperties.ConfigurationProperty("org.springframework.modulith.sample.test",
				"Some test property of type {@link java.lang.Boolean}.", "java.lang.Boolean",
				"com.acme.myproject.stereotypes.Stereotypes$SomeConfigurationProperties", "false"));
	}
}
