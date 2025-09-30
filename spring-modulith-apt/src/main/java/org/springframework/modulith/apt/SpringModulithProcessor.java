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
package org.springframework.modulith.apt;

import io.toolisticon.aptk.tools.ElementUtils;
import io.toolisticon.aptk.tools.wrapper.ElementWrapper;
import io.toolisticon.aptk.tools.wrapper.ExecutableElementWrapper;
import io.toolisticon.aptk.tools.wrapper.TypeElementWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Completion;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.StandardLocation;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter;
import org.springframework.modulith.docs.metadata.MethodMetadata;
import org.springframework.modulith.docs.metadata.TypeMetadata;
import org.springframework.modulith.docs.util.BuildSystemUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * An annotation processor to extract Javadoc from all compiled files assembling it into a JSON file located under
 * {@code $target/generated-spring-modulith/javadoc.json}.
 *
 * @author Oliver Drotbohm
 * @since 1.3
 */
public class SpringModulithProcessor implements Processor {

	private static final Collection<String> JAVADOC_TAGS = Set.of("@param", "@return", "@author", "@since", "@see");
	static final String JSON_LOCATION;

	private Elements elements;
	private Messager messager;
	private File javadocJson;
	private boolean testExecution;
	private Set<TypeMetadata> metadata = new HashSet<>();

	static {
		JSON_LOCATION = BuildSystemUtils.getTarget("generated-spring-modulith/javadoc.json");
	}

	/*
	 * (non-Javadoc)
	 * @see javax.annotation.processing.Processor#getSupportedAnnotationTypes()
	 */
	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Collections.singleton("*");
	}

	/*
	 * (non-Javadoc)
	 * @see javax.annotation.processing.Processor#getSupportedOptions()
	 */
	@Override
	public Set<String> getSupportedOptions() {
		return Collections.emptySet();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.annotation.processing.Processor#getSupportedSourceVersion()
	 */
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.annotation.processing.Processor#getCompletions(javax.lang.model.element.Element, javax.lang.model.element.AnnotationMirror, javax.lang.model.element.ExecutableElement, java.lang.String)
	 */
	@Override
	public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation,
			ExecutableElement member, String userText) {
		return Collections.emptyList();
	}

	/*
	 * (non-Javadoc)
	 * @see javax.annotation.processing.Processor#init(javax.annotation.processing.ProcessingEnvironment)
	 */
	@Override
	public void init(ProcessingEnvironment environment) {

		this.elements = environment.getElementUtils();
		this.messager = environment.getMessager();
		this.javadocJson = getAptOutputFolder(environment).resolve(JSON_LOCATION).toFile();

		try {

			var placeholder = environment.getFiler()
					.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/spring-modulith/__placeholder");

			var path = placeholder.toUri().toString();

            if (BuildSystemUtils.isTestTarget(path)) {
				this.testExecution = true;
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see javax.annotation.processing.Processor#process(java.util.Set, javax.annotation.processing.RoundEnvironment)
	 */
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

		if (testExecution) {
			return false;
		}

		if (!roundEnv.processingOver()) {

			roundEnv.getRootElements().stream()
					.map(ElementWrapper::wrap)
					.filter(ElementWrapper::isTypeElement)
					.map(TypeElementWrapper::toTypeElement)
					.flatMap(this::handle)
					.forEach(metadata::add);

			return false;
		}

		if (roundEnv.processingOver()) {

			var methodJson = JsonWriter.<MethodMetadata> of(inner -> {
				inner.add("name", MethodMetadata::name);
				inner.add("signature", MethodMetadata::signature);
				inner.add("comment", MethodMetadata::comment)
						.whenNotNull();
			});

			var typeJson = JsonWriter.<TypeMetadata> of(members -> {
				members.add("name", TypeMetadata::name);
				members.add("comment", TypeMetadata::comment)
						.whenNotNull();
				members.add("methods", TypeMetadata::methods)
						.whenNotEmpty()
						.as(methods -> {
							return methods.stream().map(methodJson::write).toList();
						});
			});

			messager.printMessage(Kind.NOTE, "Extracting Javadoc into " + javadocJson.getAbsolutePath() + ".");

			if (!javadocJson.exists()) {
				try {
					Files.createDirectories(javadocJson.toPath().getParent());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			var output = JsonWriter.standard()
					.withNewLineAtEnd()
					.writeToString(metadata.stream()
							.sorted(Comparator.comparing(TypeMetadata::name))
							.map(typeJson::write)
							.toList());

			try (var writer = new FileWriter(javadocJson)) {
				writer.write(output);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return false;
	}

	private Stream<TypeMetadata> handle(TypeElementWrapper type) {
		return getTypes(type).flatMap(this::toMetadata);
	}

	private Stream<TypeMetadata> toMetadata(TypeElementWrapper it) {

		var methods = it.getMethods().stream()
				.flatMap(this::toMetadata)
				.toList();

		var comment = getComment(it);

		return comment != null || !methods.isEmpty()
				? Stream.of(new TypeMetadata(getQualifiedName(it), comment, methods))
				: Stream.empty();
	}

	/**
	 * Workaround for https://github.com/toolisticon/aptk/issues/163
	 *
	 * @param element must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	private String getQualifiedName(TypeElementWrapper element) {

		Assert.notNull(element, "Element must not be null!");

		if (element.getNestingKind() != NestingKind.MEMBER) {
			return element.getQualifiedName();
		}

		var enclosing = ElementUtils.AccessEnclosingElements.<TypeElement> getFirstEnclosingElementOfKind(element.unwrap(),
				ElementKind.CLASS,
				ElementKind.INTERFACE,
				ElementKind.RECORD);

		return enclosing != null
				? getQualifiedName(TypeElementWrapper.wrap(enclosing)) + "$" + element.getSimpleName()
				: element.getQualifiedName();
	}

	private Stream<MethodMetadata> toMetadata(ExecutableElementWrapper method) {

		var comment = getComment(method);

		return comment != null
				? Stream.of(new MethodMetadata(method.getSimpleName(), getSignature(method), comment))
				: Stream.empty();
	}

	@Nullable
	private String getComment(ElementWrapper<?> element) {

		var result = elements.getDocComment(element.unwrap());

		if (result == null) {
			return null;
		}

		for (var tag : JAVADOC_TAGS) {

			var index = result.indexOf(tag);

			if (index == -1) {
				continue;
			}

			result = result.substring(0, index);
		}

		result = result.trim()
				.replaceAll("\\n\s*", " "); // replace newlines

		return StringUtils.hasText(result) ? result : null;
	}

	private static String getSignature(ExecutableElementWrapper wrapper) {

		var parameters = wrapper.getParameters().stream()
				.map(it -> it.asType().getBinaryName())
				.collect(Collectors.joining(", ", "(", ")"));

		return wrapper.getSimpleName() + parameters;
	}

	private static Stream<TypeElementWrapper> getTypes(TypeElementWrapper type) {

		var enclosed = type.getEnclosedElements().stream()
				.filter(ElementWrapper::isTypeElement)
				.map(TypeElementWrapper::toTypeElement);

		return Stream.concat(Stream.of(type), enclosed);
	}

	private static Path getAptOutputFolder(ProcessingEnvironment environment) {

		var boot = environment.getOptions()
				.get("org.springframework.boot.configurationprocessor.additionalMetadataLocations");

		if (boot != null) {
			return Path.of(boot.substring(0, boot.indexOf("/src/main/resources")));
		}

		var kapt = environment.getOptions().get("kapt.kotlin.generated");

		if (kapt != null) {

			// Strip Gradle or Maven suffixes
			var index = kapt.indexOf("/build/generated/source");
			index = index == -1 ? kapt.indexOf("/target/generated-sources") : index;

			return Path.of(kapt.substring(0, index));
		}

		return Path.of(".");
	}
}
