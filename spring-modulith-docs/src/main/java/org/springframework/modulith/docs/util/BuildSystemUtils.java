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
package org.springframework.modulith.docs.util;

import java.io.File;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Utilities to detect the build system used.
 *
 * @author Oliver Drotbohm
 * @author Dennis Wittkötter
 * @since 1.3
 */
public class BuildSystemUtils {

	/**
	 * Returns a path to a resource in the build target folder.
	 *
	 * @param path must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static String getTarget(String path) {

		Assert.notNull(path, "Path must not be null!");

		return getTargetFolder() + (path.startsWith("/") ? path : "/" + path);
	}

	/**
	 * Returns a {@link Resource} in the build target folder.
	 *
	 * @param path must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 */
	public static Optional<Resource> getTargetResource(String path) {
		return Optional.<Resource> of(new FileSystemResource(getTarget(path))).filter(Resource::exists);
	}

	/**
	 * Returns the path to the folder containing test classes.
	 *
	 * @return will never be {@literal null}.
	 * @deprecated since 1.4.4, 2.0. Use {@link #pointsToTestTarget(String)} instead.
	 */
	@Deprecated(since = "1.4.4, 2.0", forRemoval = true)
	public static String getTestTarget() {
		return isMaven() ? "target/test-classes" : "build/classes/java/test";
	}

	/**
	 * Returns whether the given path points to a resource in the test target folder.
	 *
	 * @param path must not be {@literal null} or empty.
	 */
	public static boolean pointsToTestTarget(String path) {

		Assert.hasText(path, "Path must not be null or empty!");

		return isMaven() ? pointsToMavenTestTarget(path) : pointsToGradleTestTarget(path);
	}

	/**
	 * Returns the path to the target folder for resources.
	 *
	 * @return will never be {@literal null}.
	 * @since 1.4
	 */
	public static String getResourceTarget() {
		return isMaven() ? "target/classes" : "build/resources/main";
	}

	static boolean pointsToMavenTestTarget(String path) {
		return path.matches("(.*\\/)?target\\/test-classes\\/.*");
	}

	static boolean pointsToGradleTestTarget(String path) {
		return path.matches("(.*\\/)?build(\\/.+)?\\/classes(\\/.+)?\\/(test|testFixtures)\\/.*");
	}

	private static String getTargetFolder() {
		return isMaven() ? "target" : "build";
	}

	private static boolean isMaven() {
		return new File("pom.xml").exists();
	}
}
