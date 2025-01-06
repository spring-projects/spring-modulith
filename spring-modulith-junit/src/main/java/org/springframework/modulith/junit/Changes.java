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
package org.springframework.modulith.junit;

import static java.util.stream.Collectors.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.modulith.junit.Changes.Change;
import org.springframework.modulith.junit.Changes.Change.OtherFileChange;
import org.springframework.modulith.junit.Changes.Change.SourceChange;
import org.springframework.modulith.junit.diff.ModifiedFile;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * A set of {@link Change}s made to a Java project.
 *
 * @author Lukas Dohmen
 * @author David Bilge
 * @author Oliver Drotbohm
 */
public class Changes implements Iterable<Change> {

	public static final Changes NONE = new Changes(Collections.emptySet());

	private final Collection<Change> changes;

	/**
	 * Creates a new {@link Changes} instance from the given {@link Change}s.
	 *
	 * @param changes must not be {@literal null}.
	 */
	private Changes(Collection<Change> changes) {

		Assert.notNull(changes, "Changes must not be null!");

		this.changes = changes;
	}

	/**
	 * Creates a new {@link Changes} instance from the given {@link ModifiedFile}s.
	 *
	 * @param files must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	static Changes of(Stream<ModifiedFile> files) {

		Assert.notNull(files, "Modified files must not be null!");

		return files.map(Change::of).collect(collectingAndThen(toSet(), Changes::new));
	}

	/**
	 * Returns whether there are any changes at all.
	 */
	boolean isEmpty() {
		return changes.isEmpty();
	}

	Set<String> getChangedClasses() {

		return filter(changes, SourceChange.class, SourceChange::fullyQualifiedClassName)
				.collect(Collectors.toSet());
	}

	boolean hasClassChanges() {
		return !getChangedClasses().isEmpty();
	}

	boolean contains(Class<?> type) {
		return changes.stream().anyMatch(it -> it.hasOrigin(type.getName()));
	}

	/**
	 * Returns whether a build-related resource has changed.
	 *
	 * @return
	 */
	boolean hasBuildResourceChanges() {

		return filter(changes, OtherFileChange.class)
				.anyMatch(OtherFileChange::affectsBuildResource);
	}

	/**
	 * Returns whether the current changes contain a change to a classpath resource, i.e. a non-source file that lives on
	 * the classpath.
	 */
	boolean hasClasspathResourceChange() {
		return changes.stream().anyMatch(it -> it instanceof OtherFileChange change && change.isClasspathResource());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Change> iterator() {
		return changes.iterator();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return changes.stream()
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
	}

	private static final <T> Stream<T> filter(Collection<?> source, Class<T> type) {
		return filter(source, type, Function.identity());
	}

	private static final <T, S> Stream<S> filter(Collection<?> source, Class<T> type, Function<T, S> mapper) {

		return source.stream()
				.filter(type::isInstance)
				.map(type::cast)
				.map(mapper);
	}

	/**
	 * A change to the local project.
	 *
	 * @author Lukas Dohmen
	 * @author David Bilge
	 * @author Oliver Drotbohm
	 */
	sealed interface Change permits SourceChange, OtherFileChange {

		/**
		 * Returns whether the change has the given origin.
		 *
		 * @param nameOrPath must not be {@literal null} or empty.
		 */
		boolean hasOrigin(String nameOrPath);

		/**
		 * creates a new
		 *
		 * @param file
		 * @return
		 */
		static Change of(ModifiedFile file) {

			if (!file.isJavaSource()) {
				return new OtherFileChange(file.path());
			}

			var withoutExtension = StringUtils.stripFilenameExtension(file.path());
			var startOfMainDir = withoutExtension.indexOf(JavaSourceChange.STANDARD_SOURCE_DIRECTORY);
			var startOfTestDir = withoutExtension.indexOf(JavaSourceChange.STANDARD_TEST_SOURCE_DIRECTORY);

			if (startOfTestDir > -1 && (startOfMainDir < 0 || startOfTestDir < startOfMainDir)) {

				var fullyQualifiedClassName = ClassUtils.convertResourcePathToClassName(
						withoutExtension.substring(startOfTestDir + JavaSourceChange.STANDARD_TEST_SOURCE_DIRECTORY.length() + 1));

				return new JavaTestSourceChange(fullyQualifiedClassName);
			}

			if (startOfMainDir > -1 && (startOfTestDir < 0 || startOfMainDir < startOfTestDir)) {

				var fullyQualifiedClassName = ClassUtils.convertResourcePathToClassName(
						withoutExtension.substring(startOfMainDir + JavaSourceChange.STANDARD_SOURCE_DIRECTORY.length() + 1));

				return new JavaSourceChange(fullyQualifiedClassName);
			}

			return new JavaSourceChange(ClassUtils.convertResourcePathToClassName(withoutExtension));
		}

		/**
		 * A change to a source file.
		 *
		 * @author Oliver Drotbohm
		 */
		sealed interface SourceChange extends Change permits JavaSourceChange, JavaTestSourceChange {

			String fullyQualifiedClassName();

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.junit.Change#hasOrigin(java.lang.String)
			 */
			@Override
			default boolean hasOrigin(String nameOrPath) {
				return fullyQualifiedClassName().equals(nameOrPath);
			}
		}

		/**
		 * A change in a Java source file.
		 *
		 * @author Lukas Dohmen
		 * @author David Bilge
		 * @author Oliver Drotbohm
		 */
		record JavaSourceChange(String fullyQualifiedClassName) implements SourceChange {

			private static final String STANDARD_SOURCE_DIRECTORY = "src/main/java";
			private static final String STANDARD_TEST_SOURCE_DIRECTORY = "src/test/java";

			/*
			 * (non-Javadoc)
			 * @see java.lang.Record#toString()
			 */
			@Override
			public final String toString() {
				return "☕ " + fullyQualifiedClassName;
			}
		}

		/**
		 * A change in a Java test source file.
		 *
		 * @author Lukas Dohmen
		 * @author David Bilge
		 * @author Oliver Drotbohm
		 */
		record JavaTestSourceChange(String fullyQualifiedClassName) implements SourceChange {}

		/**
		 * Some arbitrary file change.
		 *
		 * @author Lukas Dohmen
		 * @author David Bilge
		 * @author Oliver Drotbohm
		 */
		record OtherFileChange(String path) implements Change {

			private static final Collection<String> CLASSPATH_RESOURCES = Set.of("src/main/resources", "src/test/resources");
			private static final Collection<String> BUILD_FILES = Set.of(

					// Gradle
					"build.gradle", "build.gradle.kts", "gradle.properties", "settings.gradle", "settings.gradle.kts",

					// Maven
					"pom.xml");

			/**
			 * Returns whether the change affects a build resource.
			 */
			public boolean affectsBuildResource() {
				return BUILD_FILES.contains(path);
			}

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.junit.Change#hasOrigin(java.lang.String)
			 */
			@Override
			public boolean hasOrigin(String nameOrPath) {
				return path.equals(nameOrPath);
			}

			/**
			 * Returns whether the change affects a classpath resource (in contrast to other resources).
			 */
			public boolean isClasspathResource() {
				return CLASSPATH_RESOURCES.stream().anyMatch(path::startsWith);
			}

			/*
			 * (non-Javadoc)
			 * @see org.springframework.modulith.junit.Change.OtherFileChange#toString()
			 */
			@Override
			public final String toString() {
				return "📄 " + path;
			}
		}
	}
}
