/*
 * Copyright 2018-2024 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.util.function.ThrowingConsumer;

import com.acme.myproject.Application;

/**
 * Unit tests for {@link Documenter}.
 *
 * @author Oliver Drotbohm
 * @author Cora Iberkleid
 */
class DocumenterTest {

	Documenter documenter = new Documenter(Application.class);

	@Test
	void writesComponentStructureAsPlantUml() throws IOException {
		documenter.toPlantUml();
	}

	@Test
	void writesSingleModuleDocumentation() throws IOException {

		ApplicationModule module = documenter.getModules().getModuleByName("moduleB") //
				.orElseThrow(() -> new IllegalArgumentException());

		documenter.writeModuleAsPlantUml(module, DiagramOptions.defaults() //
				.withColorSelector(it -> Optional.of("#ff0000")) //
				.withDefaultDisplayName(it -> it.getDisplayName().toUpperCase()));

		DiagramOptions options = DiagramOptions.defaults() //
				.withComponentFilter(component -> component.getRelationships().stream()
						.anyMatch(it -> it.getTagsAsSet().contains(DependencyType.EVENT_LISTENER.toString())));

		documenter.writeModulesAsPlantUml(options);
	}

	@Test
	void testName() {

		documenter.getModules().stream() //
				.map(it -> documenter.toModuleCanvas(it));
	}

	@Test
	void customizesOutputLocation() throws Exception {

		String customOutputFolder = "build/spring-modulith";
		Path path = Paths.get(customOutputFolder);

		doWith(path, it -> {

			new Documenter(ApplicationModules.of(Application.class), customOutputFolder).writeModuleCanvases();

			assertThat(Files.list(path)).isNotEmpty();
			assertThat(path).exists();
		});
	}

	@Test // GH-638
	void createsAggregatingDocumentOnlyIfPartialsExist() throws Exception {

		var customOutputFolder = "build/spring-modulith";
		var path = Paths.get(customOutputFolder);
		var documenter = new Documenter(ApplicationModules.of(Application.class), customOutputFolder);

		doWith(path, it -> {

			// all-docs.adoc should be created
			documenter.writeDocumentation();

			// 2 per module (PlantUML + Canvas) + component overview + aggregating doc
			var expectedFiles = documenter.getModules().stream().count() * 2 + 2;

			// 3 per module (headline + PlantUML + Canvas) + component headline + component PlantUML
			var expectedLines = documenter.getModules().stream().count() * 3 + 2;

			assertThat(Files.walk(it).filter(Files::isRegularFile).count())
					.isEqualTo(expectedFiles);

			assertThat(path.resolve("all-docs.adoc")).exists().satisfies(doc -> {
				assertThat(Files.lines(doc)
						.filter(line -> !line.trim().isEmpty())
						.count()).isEqualTo(expectedLines);
			});
		});
	}

	@Test // GH-638
	void doesNotCreateAggregatingDocumentIfNoPartialsExist() throws Exception {

		var customOutputFolder = "build/spring-modulith";
		var path = Paths.get(customOutputFolder);

		doWith(path, it -> {

			var documenter = new Documenter(ApplicationModules.of(Application.class), customOutputFolder)
					.writeDocumentation();

			deleteDirectoryContents(it);

			documenter.writeAggregatingDocument();

			var aggregatingDoc = path.resolve("all-docs.adoc");

			assertThat(aggregatingDoc).doesNotExist();
		});
	}

	private static void deleteDirectoryContents(Path path) throws IOException {

		if (Files.exists(path) && Files.isDirectory(path)) {
			try (Stream<Path> walk = Files.walk(path)) {
				walk.sorted(Comparator.reverseOrder())
						.filter(p -> !p.equals(path)) // Ensure we don't delete the directory itself
						.map(Path::toFile)
						.forEach(File::delete);
			}
		}
	}

	private static void deleteDirectory(Path path) throws IOException {

		deleteDirectoryContents(path);
		Files.deleteIfExists(path);
	}

	private static void doWith(Path path, ThrowingConsumer<Path> consumer) throws Exception {

		try {
			consumer.accept(path);
		} finally {
			deleteDirectory(path);
		}
	}
}
