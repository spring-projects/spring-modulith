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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

final class PlantUmlSvgRenderer implements DiagramRenderer {

	private static final String START = "@startuml";
	private static final String LAYOUT = "!pragma layout smetana";

	@Override
	public RenderedDiagram render(String source) {

		try {

			var output = new ByteArrayOutputStream();
			var reader = new SourceStringReader(prepare(source));
			var description = reader.outputImage(output, new FileFormatOption(FileFormat.SVG));
			var svg = output.toString(StandardCharsets.UTF_8);

			if (svg.isBlank()) {
				return new RenderedDiagram(null, description == null ? "PlantUML did not produce SVG output."
						: description.getDescription());
			}

			return new RenderedDiagram(svg, null);

		} catch (Exception ex) {
			return new RenderedDiagram(null, ex.getMessage());
		}
	}

	private static String prepare(String source) {

		if (!source.contains(START) || source.contains(LAYOUT)) {
			return source;
		}

		return source.replaceFirst(START, START + System.lineSeparator() + System.lineSeparator() + LAYOUT);
	}
}


