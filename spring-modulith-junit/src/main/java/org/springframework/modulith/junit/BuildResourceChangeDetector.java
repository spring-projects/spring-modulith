/*
 * Copyright 2024-2026 the original author or authors.
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
package org.springframework.modulith.junit;

import java.util.Set;

/**
 * Detects build system resources that should make optimized test execution back off.
 *
 * @author char-yb
 */
final class BuildResourceChangeDetector {

	private static final Set<String> EXACT_PATHS = Set.of(

			// Build entrypoints
			"pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",

			// Gradle metadata
			"gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties");

	private static final Set<String> SUFFIXES = Set.of("/pom.xml", "/build.gradle", ".gradle.kts",
			".settings.gradle.kts", ".init.gradle.kts", "/settings.gradle", "/gradle.properties");

	private static final Set<String> DIRECTORY_PREFIXES = Set.of("buildSrc", "build-logic");

	private BuildResourceChangeDetector() {}

	static boolean isBuildResource(String path) {

		var normalizedPath = path.replace('\\', '/');

		return EXACT_PATHS.contains(normalizedPath)
				|| SUFFIXES.stream().anyMatch(normalizedPath::endsWith)
				|| DIRECTORY_PREFIXES.stream().anyMatch(it -> normalizedPath.equals(it) || normalizedPath.startsWith(it + "/"));
	}
}
