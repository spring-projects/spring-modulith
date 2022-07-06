/*
 * Copyright 2020 the original author or authors.
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
package org.moduliths.docs;

import static org.springframework.util.ClassUtils.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.moduliths.docs.ConfigurationProperties.ModuleProperty;
import org.moduliths.docs.Documenter.CanvasOptions;
import org.moduliths.docs.Documenter.CanvasOptions.Groupings;
import org.moduliths.model.ArchitecturallyEvidentType;
import org.moduliths.model.EventType;
import org.moduliths.model.FormatableJavaClass;
import org.moduliths.model.Module;
import org.moduliths.model.Modules;
import org.moduliths.model.Source;
import org.moduliths.model.SpringBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * @author Oliver Drotbohm
 */
class Asciidoctor {

	private static String PLACEHOLDER = "¯\\_(ツ)_/¯";
	private static final Pattern JAVADOC_CODE = Pattern.compile("\\{\\@(?>link|code|literal)\\s(.*)\\}");

	private final Modules modules;
	private final String javaDocBase;
	private final Optional<DocumentationSource> docSource;

	private Asciidoctor(Modules modules, String javaDocBase) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.hasText(javaDocBase, "Javadoc base must not be null or empty!");

		this.javaDocBase = javaDocBase;
		this.modules = modules;
		this.docSource = Optional.of("capital.scalable.restdocs.javadoc.JavadocReaderImpl")
				.filter(it -> ClassUtils.isPresent(it, Asciidoctor.class.getClassLoader()))
				.map(__ -> new SpringAutoRestDocsDocumentationSource())
				.map(it -> new CodeReplacingDocumentationSource(it, this));
	}

	/**
	 * Creates a new {@link Asciidoctor} instance for the given {@link Modules} and Javadoc base URI.
	 *
	 * @param modules must not be {@literal null}.
	 * @param javadocBase can be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Asciidoctor withJavadocBase(Modules modules, @Nullable String javadocBase) {
		return new Asciidoctor(modules, javadocBase == null ? PLACEHOLDER : javadocBase);
	}

	/**
	 * Creates a new {@link Asciidoctor} instance for the given {@link Modules}.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Asciidoctor withoutJavadocBase(Modules modules) {
		return new Asciidoctor(modules, PLACEHOLDER);
	}

	/**
	 * Turns the given source string into inline code.
	 *
	 * @param source must not be {@literal null}.
	 * @return
	 */
	public String toInlineCode(String source) {

		String[] parts = source.split("#");

		String type = parts[0];
		Optional<String> methodSignature = parts.length == 2 ? Optional.of(parts[1]) : Optional.empty();

		return modules.getModuleByType(type)
				.flatMap(it -> it.getType(type))
				.map(it -> toOptionalLink(it, methodSignature))
				.orElseGet(() -> String.format("`%s`", type));
	}

	public String toInlineCode(JavaClass type) {
		return toOptionalLink(type);
	}

	public String toInlineCode(SpringBean bean) {

		String base = toInlineCode(bean.toArchitecturallyEvidentType());

		List<JavaClass> interfaces = bean.getInterfacesWithinModule();

		if (interfaces.isEmpty()) {
			return base;
		}

		String interfacesAsString = interfaces.stream() //
				.map(this::toInlineCode) //
				.collect(Collectors.joining(", "));

		return String.format("%s implementing %s", base, interfacesAsString);
	}

	public String renderSpringBeans(CanvasOptions options, Module module) {

		StringBuilder builder = new StringBuilder();
		Groupings groupings = options.groupBeans(module);

		if (groupings.hasOnlyFallbackGroup()) {
			return toBulletPoints(groupings.byGrouping(CanvasOptions.FALLBACK_GROUP));
		}

		groupings.forEach((grouping, beans) -> {

			if (beans.isEmpty()) {
				return;
			}

			if (builder.length() != 0) {
				builder.append("\n\n");
			}

			builder.append("_").append(grouping.getName()).append("_");

			if (grouping.getDescription() != null) {
				builder.append(" -- ").append(grouping.getDescription());
			}

			builder.append("\n\n");
			builder.append(toBulletPoints(beans));

		});

		return builder.length() == 0 ? "None" : builder.toString();
	}

	public String renderEvents(Module module) {

		List<EventType> events = module.getPublishedEvents();

		if (events.isEmpty()) {
			return "none";
		}

		StringBuilder builder = new StringBuilder();

		for (EventType eventType : events) {

			builder.append("* ")
					.append(toInlineCode(eventType.getType()));

			if (!eventType.hasSources()) {
				builder.append("\n");
			} else {
				builder.append(" created by:\n");
			}

			for (Source source : eventType.getSources()) {

				builder.append("** ")
						.append(toInlineCode(source.toString(module)))
						.append("\n");
			}
		}

		return builder.toString();
	}

	public String renderConfigurationProperties(Module module, List<ModuleProperty> properties) {

		if (properties.isEmpty()) {
			return "none";
		}

		Stream<String> stream = properties.stream()
				.map(it -> {

					StringBuilder builder = new StringBuilder()
							.append(toCode(it.getName()))
							.append(" -- ")
							.append(toInlineCode(it.getType()));

					String defaultValue = it.getDefaultValue();

					if (defaultValue != null && StringUtils.hasText(defaultValue)) {

						builder = builder.append(", default ")
								.append(toInlineCode(defaultValue))
								.append("");
					}

					String description = it.getDescription();

					if (description != null && StringUtils.hasText(description)) {
						builder = builder.append(". ")
								.append(toAsciidoctor(description));
					}

					return builder.toString();
				});

		return toBulletPoints(stream);
	}

	private String toBulletPoints(List<SpringBean> beans) {
		return toBulletPoints(beans.stream().map(this::toInlineCode));
	}

	public String typesToBulletPoints(List<JavaClass> types) {
		return toBulletPoints(types.stream() //
				.map(this::toOptionalLink));
	}

	private String toBulletPoints(Stream<String> types) {

		return types//
				.collect(Collectors.joining("\n* ", "* ", ""));
	}

	public String toBulletPoint(String source) {
		return String.format("* %s", source);
	}

	private String toOptionalLink(JavaClass source) {
		return toOptionalLink(source, Optional.empty());
	}

	private String toOptionalLink(JavaClass source, Optional<String> methodSignature) {

		Module module = modules.getModuleByType(source).orElse(null);
		String typeAndMethod = toCode(
				toTypeAndMethod(FormatableJavaClass.of(source).getAbbreviatedFullName(module), methodSignature));

		if (module == null
				|| !source.getModifiers().contains(JavaModifier.PUBLIC)
				|| !module.contains(source)) {
			return typeAndMethod;
		}

		String classPath = convertClassNameToResourcePath(source.getFullName()) //
				.replace('$', '.');

		return Optional.ofNullable(javaDocBase == PLACEHOLDER ? null : javaDocBase) //
				.map(it -> it.concat("/").concat(classPath).concat(".html")) //
				.map(it -> toLink(typeAndMethod, it)) //
				.orElseGet(() -> typeAndMethod);
	}

	private static String toTypeAndMethod(String type, Optional<String> methodSignature) {
		return methodSignature
				.map(it -> type.concat("#").concat(it))
				.orElse(type);
	}

	private String toInlineCode(ArchitecturallyEvidentType type) {

		if (type.isEventListener()) {

			if (!docSource.isPresent()) {

				Stream<JavaClass> referenceTypes = type.getReferenceTypes();

				return String.format("%s listening to %s", //
						toInlineCode(type.getType()), //
						toInlineCode(referenceTypes));
			}

			String header = String.format("%s listening to:\n", toInlineCode(type.getType()));

			return header + type.getReferenceMethods().map(it -> {

				JavaMethod method = it.getMethod();
				Assert.isTrue(method.getRawParameterTypes().size() > 0,
						() -> String.format("Method %s must have at least one parameter!", method));

				JavaClass parameterType = it.getMethod().getRawParameterTypes().get(0);
				String isAsync = it.isAsync() ? "(async) " : "";

				return docSource.flatMap(source -> source.getDocumentation(it.getMethod()))
						.map(doc -> String.format("** %s %s-- %s", toInlineCode(parameterType), isAsync, doc))
						.orElseGet(() -> String.format("** %s %s", toInlineCode(parameterType), isAsync));

			}).collect(Collectors.joining("\n"));
		}

		return toInlineCode(type.getType());
	}

	private String toInlineCode(Stream<JavaClass> types) {

		return types.map(this::toInlineCode) //
				.collect(Collectors.joining(", "));
	}

	private static String toLink(String source, String href) {
		return String.format("link:%s[%s]", href, source);
	}

	private static String toCode(String source) {
		return String.format("`%s`", source);
	}

	public static String startTable(String tableSpec) {
		return String.format("[%s]\n|===\n", tableSpec);
	}

	public static String startOrEndTable() {
		return "|===\n";
	}

	public static String writeTableRow(String... columns) {

		return Stream.of(columns) //
				.collect(Collectors.joining("\n|", "|", "\n"));
	}

	public String toAsciidoctor(String source) {

		Matcher matcher = JAVADOC_CODE.matcher(source);

		while (matcher.find()) {

			String type = matcher.group(1);

			source = source.replace(matcher.group(), toInlineCode(type));
		}

		return source;
	}
}
