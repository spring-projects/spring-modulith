/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter.Options;
import org.springframework.modulith.model.Module;
import org.springframework.modulith.model.Module.DependencyType;

import com.acme.myproject.Application;

/**
 * Unit tests for {@link Documenter}.
 *
 * @author Oliver Drotbohm
 */
class DocumenterTest {

	Documenter documenter = new Documenter(Application.class);

	@Test
	void writesComponentStructureAsPlantUml() throws IOException {
		documenter.toPlantUml();
	}

	@Test
	void writesSingleModuleDocumentation() throws IOException {

		Module module = documenter.getModules().getModuleByName("moduleB") //
				.orElseThrow(() -> new IllegalArgumentException());

		documenter.writeModuleAsPlantUml(module, Options.defaults() //
				.withColorSelector(it -> Optional.of("#ff0000")) //
				.withDefaultDisplayName(it -> it.getDisplayName().toUpperCase()));

		Options options = Options.defaults() //
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
	void customizesOutputLocation() throws IOException {

		String customOutputFolder = "build/spring-modulith";
		Path path = Paths.get(customOutputFolder);

		try {

			documenter.withOutputFolder(customOutputFolder).writeModuleCanvases();

			assertThat(Files.list(path)).isNotEmpty();
			assertThat(path).exists();

		} finally {

			Files.walk(path)
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		}
	}
}
