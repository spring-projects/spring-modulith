/*
 * Copyright 2020-2025 the original author or authors.
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

import static org.slf4j.LoggerFactory.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * A {@link DocumentationSource} that uses metadata generated by Spring Auto REST Docs' Javadoc Doclet.
 *
 * @author Oliver Drotbohm
 * @deprecated since 1.3, use {@link SpringModulithDocumentationSource} instead.
 */
@Deprecated
enum SpringAutoRestDocsDocumentationSource implements DocumentationSource {

	INSTANCE;

	static {
		Assert.isTrue(
				ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper",
						SpringModulithDocumentationSource.class.getClassLoader()),
				"Jackson is required on the classpath for Spring Auto RESTDocs generated Javadoc metadata!");
	}

	private final JavadocReader reader = JavadocReader.createWithSystemProperty();

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.docs.JavadocSource#getDocumentation(com.tngtech.archunit.core.domain.JavaMethod)
	 */
	@Override
	public Optional<String> getDocumentation(JavaMethod method) {
		return Optional.of(reader.resolveMethodComment(method.getOwner().reflect(), method.getName()))
				.filter(it -> !it.isEmpty());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.modulith.docs.DocumentationSource#getDocumentation(com.tngtech.archunit.core.domain.JavaClass)
	 */
	@Override
	public Optional<String> getDocumentation(JavaClass type) {
		return Optional.of(reader.resolveClassComment(type.reflect()))
				.filter(Predicate.not(String::isEmpty));
	}

	static class ClassJavadoc {

		private String comment;
		private Map<String, MethodJavadoc> methods = new HashMap<>();

		public String getClassComment() {
			return comment;
		}

		public String getMethodComment(String methodName) {
			MethodJavadoc methodJavadoc = methods.get(methodName);
			if (methodJavadoc != null) {
				return trimToEmpty(methodJavadoc.getComment());
			} else {
				return "";
			}
		}

		private static String trimToEmpty(@Nullable String source) {
			return source == null ? "" : source.trim();
		}

		static class MethodJavadoc {
			private String comment;
			private Map<String, String> parameters = new HashMap<>();
			private Map<String, String> tags = new HashMap<>();

			public String getComment() {
				return comment;
			}

			public String getParameterComment(String parameterName) {
				return parameters.get(parameterName);
			}

			public String getTag(String tagName) {
				return tags.get(tagName);
			}
		}
	}

	static class JavadocReader {

		private static final Logger log = getLogger(JavadocReader.class);
		private static final String PATH_DELIMITER = ",";
		private static final String JAVADOC_JSON_DIR_PROPERTY = "org.springframework.restdocs.javadocJsonDir";

		private final Map<String, ClassJavadoc> classCache = new ConcurrentHashMap<>();
		private final ObjectMapper mapper;
		private final List<File> absoluteBaseDirs;

		private JavadocReader(ObjectMapper mapper, List<File> absoluteBaseDirs) {
			this.mapper = mapper;
			this.absoluteBaseDirs = absoluteBaseDirs;
		}

		public static JavadocReader createWithSystemProperty() {
			String jsonDir = System.getProperties().getProperty(JAVADOC_JSON_DIR_PROPERTY);
			if (!StringUtils.hasText(jsonDir)) {
				jsonDir = getDefaultJsonDirectory();
			}
			return new JavadocReader(objectMapper(), toAbsoluteDirs(jsonDir));
		}

		private static String getDefaultJsonDirectory() {
			if (new File("pom.xml").exists()) {
				return "target/generated-javadoc-json";
			}
			return "build/generated-javadoc-json";
		}

		/**
		 * Used for testing.
		 */
		static JavadocReader createWith(String javadocJsonDir) {
			return new JavadocReader(objectMapper(), toAbsoluteDirs(javadocJsonDir));
		}

		public String resolveMethodComment(Class<?> javaBaseClass, final String javaMethodName) {
			return resolveCommentFromClassHierarchy(javaBaseClass,
					classJavadoc -> classJavadoc.getMethodComment(javaMethodName));
		}

		public String resolveClassComment(Class<?> javaBaseClass) {
			return classJavadoc(javaBaseClass).getClassComment();
		}

		private ClassJavadoc classJavadoc(Class<?> clazz) {
			String relativePath = classToRelativePath(clazz);
			ClassJavadoc classJavadocFromCache = classCache.get(relativePath);
			if (classJavadocFromCache != null) {
				return classJavadocFromCache;
			} else {
				ClassJavadoc classJavadoc = readFiles(clazz, relativePath);
				classCache.put(relativePath, classJavadoc);
				return classJavadoc;
			}
		}

		private String classToRelativePath(Class<?> clazz) {
			String packageName = clazz.getPackage().getName();
			String packageDir = packageName.replace(".", File.separator);
			String className = clazz.getCanonicalName().replaceAll(packageName + "\\.?", "");
			return new File(packageDir, className + ".json").getPath();
		}

		private ClassJavadoc readFiles(Class<?> clazz, String relativePath) {
			if (absoluteBaseDirs.isEmpty()) {
				// No absolute directory is configured and thus we try to find the file relative.
				ClassJavadoc classJavadoc = readJson(new File(relativePath));
				if (classJavadoc != null) {
					return classJavadoc;
				}
			} else {
				// Try to find the file in all configured directories.
				for (File dir : absoluteBaseDirs) {
					ClassJavadoc classJavadoc = readJson(new File(dir, relativePath));
					if (classJavadoc != null) {
						return classJavadoc;
					}
				}
			}

			// might be in some jar on the classpath
			URL url = getClass().getClassLoader().getResource(relativePath);
			if (url != null) {
				return readJson(url);
			}

			log.debug("No Javadoc found for class {} in any of the found JSON files", clazz.getCanonicalName());
			return new ClassJavadoc();
		}

		private ClassJavadoc readJson(File docSource) {
			try {
				return mapper
						.readerFor(ClassJavadoc.class)
						.readValue(docSource);
			} catch (FileNotFoundException e) {
				// Ignored as we might try more than one file and we warn if no Javadoc file
				// is found at the end.
			} catch (IOException e) {
				log.error("Failed to read file {}", docSource.getName(), e);
			}
			return null;
		}

		private ClassJavadoc readJson(URL docSource) {
			try {
				return mapper
						.readerFor(ClassJavadoc.class)
						.readValue(docSource);
			} catch (IOException e) {
				log.error("Failed to read url {}", docSource, e);
			}
			return null;
		}

		private static ObjectMapper objectMapper() {
			ObjectMapper mapper = new ObjectMapper();
			mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
					.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
					.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
					.withSetterVisibility(JsonAutoDetect.Visibility.NONE)
					.withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
			return mapper;
		}

		private static List<File> toAbsoluteDirs(String javadocJsonDirs) {
			List<File> absoluteDirs = new ArrayList<>();
			if (StringUtils.hasText(javadocJsonDirs)) {
				String[] dirs = javadocJsonDirs.split(PATH_DELIMITER);
				for (String dir : dirs) {
					if (StringUtils.hasText(dir)) {
						absoluteDirs.add(new File(dir.trim()).getAbsoluteFile());
					}
				}
			}
			return absoluteDirs;
		}

		/**
		 * Walks up the class hierarchy and interfaces until a comment is found or top most class is reached.
		 * <p>
		 * Javadoc on super classes and Javadoc on interfaces of super classes has precedence over the Javadoc on direct
		 * interfaces of the class. This is only important in the rare case of competing Javadoc comments.
		 * <p>
		 * As we do not know the full method signature here, we can not check whether a method in the super class actually
		 * overwrites the given method. However, the Javadoc model ignores method signatures anyway and it should not cause
		 * issues for the usual use case.
		 */
		private String resolveCommentFromClassHierarchy(Class<?> javaBaseClass,
				CommentExtractor commentExtractor) {
			String comment = commentExtractor.comment(classJavadoc(javaBaseClass));
			if (StringUtils.hasText(comment)) {
				// Direct Javadoc on a method always wins.
				return comment;
			}
			// Super class has precedence over interfaces, but this also means that interfaces
			// of super classes have precedence over interfaces of the class itself.
			if (javaBaseClass.getSuperclass() != null) {
				String superClassComment = resolveCommentFromClassHierarchy(javaBaseClass.getSuperclass(),
						commentExtractor);
				if (StringUtils.hasText(superClassComment)) {
					return superClassComment;
				}
			}
			for (Class<?> i : javaBaseClass.getInterfaces()) {
				String interfaceComment = resolveCommentFromClassHierarchy(i, commentExtractor);
				if (StringUtils.hasText(interfaceComment)) {
					return interfaceComment;
				}
			}
			return "";
		}

		private interface CommentExtractor {
			String comment(ClassJavadoc classJavadoc);
		}
	}
}
