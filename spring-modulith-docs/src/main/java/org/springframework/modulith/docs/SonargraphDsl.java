/*
 * Copyright 2025 the original author or authors.
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

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;
import org.springframework.modulith.core.NamedInterfaces;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * @author Oliver Drotbohm
 */
public class SonargraphDsl {

	public String createDsl(ApplicationModules modules) {
		return modules.stream()
				.filter(it -> modules.getParentOf(it).isEmpty())
				.map(it -> createModuleDsl(it, modules))
				.collect(Collectors.joining("\n\n"));
	}

	private String createModuleDsl(ApplicationModule module, ApplicationModules modules) {

		var declaration = """
				artifact %s {

					include %s


				""".formatted(
				module.getDisplayName(),
				module.getBasePackage().getName().replace(".", "/").concat("/**"));

		declaration += module.getNamedInterfaces().stream()
				.flatMap(it -> createNamedInterface(it, module.getNamedInterfaces()))
				.map("\t"::concat)
				.collect(Collectors.joining("\n"));

		modules.stream().filter(it -> modules.getParentOf(it).filter(module::equals).isPresent())
				.map(it -> createdNested(it, modules))
				.forEach(declaration::concat);

		declaration += "\n";

		return declaration + "}";
	}

	private String createdNested(ApplicationModule module, ApplicationModules modules) {
		return "hidden " + createModuleDsl(module, modules);
	}

	private Stream<String> createNamedInterface(NamedInterface namedInterface, NamedInterfaces namedInterfaces) {

		var name = namedInterface.isUnnamed() ? "default" : namedInterface.getName();

		return Stream.of("""
				interface %s {
				%s
				}
				""".formatted(
				name,
				!namedInterfaces.hasExplicitInterfaces() && namedInterface.isUnnamed()
						? "\tinclude \"*\"\n"
						: namedInterface.asJavaClasses()
								.map(JavaClass::getName)
								.map("\tinclude "::concat)
								.collect(Collectors.joining("\n")))
				.split("\n"));
	}
}
