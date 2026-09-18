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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions.DiagramStyle;
import org.springframework.util.Assert;

final class ModulithSiteReportGenerator {

	private static final String OVERVIEW_FILE = "components.puml";
	private static final String MODULE_FILE_PATTERN = "module-%s.puml";

	private final DiagramRenderer renderer;

	ModulithSiteReportGenerator(DiagramRenderer renderer) {
		this.renderer = renderer;
	}

	void generate(ApplicationModules modules, Path reportDirectory, DiagramStyle diagramStyle, boolean renderDiagrams) {

		Assert.notNull(modules, "ApplicationModules must not be null!");
		Assert.notNull(reportDirectory, "Report directory must not be null!");
		Assert.notNull(diagramStyle, "Diagram style must not be null!");

		try {

			Files.createDirectories(reportDirectory);

			var assets = reportDirectory.resolve("assets");
			Files.createDirectories(assets);

			var diagrams = generateDiagramArtifacts(modules, assets, diagramStyle, renderDiagrams);
			Files.writeString(reportDirectory.resolve("index.html"), renderHtml(modules, diagramStyle, diagrams),
					StandardCharsets.UTF_8);

		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private List<GeneratedDiagram> generateDiagramArtifacts(ApplicationModules modules, Path assets,
			DiagramStyle diagramStyle, boolean renderDiagrams) throws IOException {

		var tempDirectory = Files.createTempDirectory("spring-modulith-site-report-");

		try {

			var options = Documenter.Options.defaults().withOutputFolder(tempDirectory.toString()).withoutClean();
			var documenter = new Documenter(modules, options);
			var diagramOptions = DiagramOptions.defaults().withStyle(diagramStyle);

			documenter.writeModulesAsPlantUml(diagramOptions);
			documenter.writeIndividualModulesAsPlantUml(diagramOptions);

			var result = new ArrayList<GeneratedDiagram>();
			result.add(copyDiagram("Overview", "Application module overview", OVERVIEW_FILE, tempDirectory, assets,
					renderDiagrams));

			for (ApplicationModule module : modules) {
				var moduleId = module.getIdentifier().toString();
				var fileName = MODULE_FILE_PATTERN.formatted(moduleId);
				result.add(copyDiagram(module.getDisplayName(), "Dependencies of module '" + moduleId + "'", fileName,
						tempDirectory, assets, renderDiagrams));
			}

			return result;

		} finally {
			deleteRecursively(tempDirectory);
		}
	}

	private GeneratedDiagram copyDiagram(String title, String description, String fileName, Path sourceDirectory,
			Path assets, boolean renderDiagrams) throws IOException {

		var source = Files.readString(sourceDirectory.resolve(fileName), StandardCharsets.UTF_8);
		var pumlTarget = assets.resolve(fileName);
		Files.writeString(pumlTarget, source, StandardCharsets.UTF_8);

		String svgFile = null;
		String failureMessage = null;

		if (renderDiagrams) {

			var rendered = renderer.render(source);
			failureMessage = rendered.failureMessage();

			if (rendered.hasSvg()) {
				svgFile = fileName.replace(".puml", ".svg");
				Files.writeString(assets.resolve(svgFile), rendered.svg(), StandardCharsets.UTF_8);
			}
		}

		return new GeneratedDiagram(title, description, "assets/" + fileName,
				svgFile == null ? null : "assets/" + svgFile, source, failureMessage);
	}

	private String renderHtml(ApplicationModules modules, DiagramStyle diagramStyle, List<GeneratedDiagram> diagrams) {

		var html = new StringBuilder();

		html.append("<!DOCTYPE html>\n")
				.append("<html lang=\"en\">\n")
				.append("<head>\n")
				.append("  <meta charset=\"UTF-8\">\n")
				.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
				.append("  <title>Spring Modulith Report</title>\n")
				.append("  <style>")
				.append("body{font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:2rem auto;max-width:1200px;padding:0 1rem;line-height:1.5;color:#1f2937;}")
				.append("h1,h2{color:#111827;}")
				.append(".meta{color:#4b5563;margin-bottom:2rem;}")
				.append(".diagram{border:1px solid #e5e7eb;border-radius:10px;padding:1rem;margin:1.5rem 0;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.04);}")
				.append(".diagram img{max-width:100%;height:auto;border:1px solid #f3f4f6;border-radius:6px;background:#fff;}")
				.append(".links{margin:.75rem 0;}")
				.append(".links a{margin-right:1rem;}")
				.append("details{margin-top:1rem;}")
				.append("pre{overflow:auto;background:#111827;color:#f9fafb;padding:1rem;border-radius:8px;}")
				.append(".note{padding:.75rem 1rem;border-left:4px solid #f59e0b;background:#fffbeb;margin:1rem 0;color:#92400e;}")
				.append("</style>\n")
				.append("</head>\n")
				.append("<body>\n")
				.append("  <h1>Spring Modulith Report</h1>\n")
				.append("  <p class=\"meta\">Generated ")
				.append(escapeHtml(Instant.now().toString()))
				.append(" from ")
				.append(escapeHtml(modules.getSource().toString()))
				.append(" using diagram style ")
				.append(escapeHtml(diagramStyle.name()))
				.append(".</p>\n");

		for (GeneratedDiagram diagram : diagrams) {
			html.append("  <section class=\"diagram\">\n")
					.append("    <h2>")
					.append(escapeHtml(diagram.title()))
					.append("</h2>\n")
					.append("    <p>")
					.append(escapeHtml(diagram.description()))
					.append("</p>\n")
					.append("    <p class=\"links\"><a href=\"")
					.append(escapeHtml(diagram.pumlLocation()))
					.append("\">PlantUML source</a>");

			if (diagram.svgLocation() != null) {
				html.append("<a href=\"")
						.append(escapeHtml(diagram.svgLocation()))
						.append("\">SVG</a>");
			}

			html.append("</p>\n");

			if (diagram.svgLocation() != null) {
				html.append("    <p><img src=\"")
						.append(escapeHtml(diagram.svgLocation()))
						.append("\" alt=\"")
						.append(escapeHtml(diagram.title()))
						.append("\"></p>\n");
			} else {
				html.append("    <div class=\"note\">No SVG preview is available for this diagram.");

				if (diagram.failureMessage() != null && !diagram.failureMessage().isBlank()) {
					html.append(" PlantUML rendering reported: ")
							.append(escapeHtml(diagram.failureMessage()))
							.append('.');
				}

				html.append(" The exact PlantUML source is included below.</div>\n");
			}

			html.append("    <details><summary>Show PlantUML source</summary><pre>")
					.append(escapeHtml(diagram.source()))
					.append("</pre></details>\n")
					.append("  </section>\n");
		}

		html.append("</body>\n</html>\n");

		return html.toString();
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private static void deleteRecursively(Path directory) {

		if (directory == null) {
			return;
		}

		try {
			if (!Files.exists(directory)) {
				return;
			}

			Files.walk(directory)
					.sorted((left, right) -> right.compareTo(left))
					.forEach(path -> {
						try {
							Files.deleteIfExists(path);
						} catch (IOException ex) {
							throw new UncheckedIOException(ex);
						}
					});
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private record GeneratedDiagram(String title, String description, String pumlLocation, String svgLocation,
			String source, String failureMessage) {}
}

