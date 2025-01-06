/*
 * Copyright 2018-2025 the original author or authors.
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
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.modulith.docs.Documenter.Options;

import com.acme.myproject.Application;

/**
 * Unit tests for {@link Documenter}.
 *
 * @author Oliver Drotbohm
 * @author Cora Iberkleid
 * @author Tobias Haindl
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

		doWith("target/custom-spring-modulith", (path, documenter) -> {

			documenter.writeModuleCanvases();

			assertThat(Files.list(path)).isNotEmpty();
			assertThat(path).exists();
		});
	}

	@Test // GH-638
	void createsAggregatingDocumentOnlyIfPartialsExist() throws Exception {

		doWith("build/spring-modulith", (path, documenter) -> {

			// all-docs.adoc should be created
			documenter.writeDocumentation();

			var numberOfModules = documenter.getModules().stream().count();

			// 2 per module (PlantUML + Canvas) + component overview + aggregating doc
			var expectedFiles = numberOfModules * 2 + 2;

			// 3 per module (headline + PlantUML + Canvas) + component headline + component PlantUML
			var expectedLines = numberOfModules * 3 + 2;

			assertThat(Files.walk(path).filter(Files::isRegularFile).count())
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

		doWith("build/spring-modulith", (path, documenter) -> {

			documenter.writeDocumentation();

			deleteDirectoryContents(path);

			documenter.writeAggregatingDocument();

			var aggregatingDoc = path.resolve("all-docs.adoc");

			assertThat(aggregatingDoc).doesNotExist();
		});
	}

	@Test // GH-644
	void cleansOutputDirectoryByDefault(@TempDir Path outputDirectory) {

		doWith(outputDirectory.toString(), (path, documenter) -> {

			var filePath = createTestFile(path);
			var nestedFiledPath = createTestFileInSubdirectory(path);

			documenter.writeDocumentation();

			assertThat(filePath).doesNotExist();
			assertThat(nestedFiledPath).doesNotExist();
			assertThat(Files.list(path)).isNotEmpty();
		});

	}

	@Test // GH-644
	void doesNotCleanOutputDirectoryIfConfigured(@TempDir Path outputDirectory) throws IOException {

		doWith(outputDirectory.toString(), it -> it.withoutClean(), (path, documenter) -> {

			var filePath = createTestFile(path);
			var nestedFiledPath = createTestFileInSubdirectory(path);

			documenter.writeDocumentation();

			assertThat(filePath).exists();
			assertThat(nestedFiledPath).exists();
			assertThat(Files.list(path)).isNotEmpty();
		});
	}

	private static Path createTestFile(Path tempDir) throws IOException {
		return createFile(tempDir.resolve("some-old-module.adoc"));
	}

	private static Path createTestFileInSubdirectory(Path tempDir) throws IOException {
		return createFile(tempDir.resolve("some-subdirectory").resolve("old-module.adoc"));
	}

	private static Path createFile(Path filePath) throws IOException {
		return Files.createDirectories(filePath);
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

	private static void doWith(String path, ThrowingBiConsumer<Path, Documenter> consumer) {
		doWith(path, Function.identity(), consumer);
	}

	private static void doWith(String path, Function<Options, Options> customizer,
			ThrowingBiConsumer<Path, Documenter> consumer) {

		var options = customizer.apply(Options.defaults().withOutputFolder(path));
		var modules = ApplicationModules.of(Application.class);

		try {
			consumer.accept(Path.of(path), new Documenter(modules, options));
		} catch (Exception o_O) {
			throw new RuntimeException(o_O);
		} finally {
			options.getOutputFolder().deleteIfExists();
		}
	}

	private interface ThrowingBiConsumer<T, S> {
		void accept(T t, S s) throws Exception;
	}
}
