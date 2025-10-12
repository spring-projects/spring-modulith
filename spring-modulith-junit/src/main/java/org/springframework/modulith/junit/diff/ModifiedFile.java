/*
 * Copyright 2024-2025 the original author or authors.
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
package org.springframework.modulith.junit.diff;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A modified file.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
public record ModifiedFile(String path) {

	/**
	 * Returns whether the modified file is a Java source file.
	 */
	public boolean isJavaSource() {
		return "java".equalsIgnoreCase(StringUtils.getFilenameExtension(path));
	}

	/**
	 * Returns whether the modified file is Kotlin source file.
	 */
	public boolean isKotlinSource() {
		return "kt".equalsIgnoreCase(StringUtils.getFilenameExtension(path));
	}

	public static Stream<ModifiedFile> of(String... paths) {
		return Arrays.stream(paths).map(ModifiedFile::new);
	}

	/**
	 * Returns the current {@link ModifiedFile} as relative to the given reference path. I.e., a {@code foo/bar.txt} with
	 * a reference of {@code foo} would result in {@code bar.txt}.
	 *
	 * @param reference must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	ModifiedFile asRelativeTo(String reference) {

		Assert.notNull(reference, "Path must not be null!");
		Assert.isTrue(reference.startsWith(reference),
				() -> "Modified file at %s is not located in %s!".formatted(reference, reference));

		return reference.isEmpty() ? this : new ModifiedFile(this.path.substring(reference.length() + 1));
	}
}
