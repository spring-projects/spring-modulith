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
package org.springframework.modulith.maven;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.maven.sample.SampleApplication;
import org.springframework.modulith.docs.Documenter.DiagramOptions.DiagramStyle;

import com.tngtech.archunit.core.importer.ImportOption;

class ModulithSiteReportGeneratorUnitTests {

	@TempDir Path tempDirectory;

	@Test
	void generatesHtmlAndDiagramAssets() throws Exception {

		var modules = ApplicationModules.of(SampleApplication.class, new ImportOption.OnlyIncludeTests());
		var generator = new ModulithSiteReportGenerator(new PlantUmlSvgRenderer());
		var reportDirectory = tempDirectory.resolve("modulith");

		generator.generate(modules, reportDirectory, DiagramStyle.UML, true);

		assertThat(reportDirectory.resolve("index.html")).exists();
		assertThat(reportDirectory.resolve("assets/components.puml")).exists();
		assertThat(reportDirectory.resolve("assets/module-order.puml")).exists();
		assertThat(reportDirectory.resolve("assets/module-inventory.puml")).exists();
		assertThat(reportDirectory.resolve("assets/components.svg")).exists();

		String html = Files.readString(reportDirectory.resolve("index.html"));
		assertThat(html).contains("Spring Modulith Report");
		assertThat(html).contains("assets/components.puml");
		assertThat(html).contains("assets/module-order.svg");
		assertThat(html).contains("Show PlantUML source");

		String overview = Files.readString(reportDirectory.resolve("assets/components.puml"));
		assertThat(overview).contains("Inventory");
		assertThat(overview).contains("Order");
	}

	@Test
	void fallsBackToPlantUmlSourceIfSvgCannotBeRendered() throws Exception {

		var modules = ApplicationModules.of(SampleApplication.class, new ImportOption.OnlyIncludeTests());
		var generator = new ModulithSiteReportGenerator(source -> new RenderedDiagram(null, "boom"));
		var reportDirectory = tempDirectory.resolve("fallback");

		generator.generate(modules, reportDirectory, DiagramStyle.UML, true);

		assertThat(reportDirectory.resolve("assets/components.puml")).exists();
		assertThat(reportDirectory.resolve("assets/components.svg")).doesNotExist();
		assertThat(Files.readString(reportDirectory.resolve("index.html")))
				.contains("No SVG preview is available")
				.contains("boom");
	}
}


