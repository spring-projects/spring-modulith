/*
 * Copyright 2020-2024 the original author or authors.
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

import static org.springframework.util.ClassUtils.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.ArchitecturallyEvidentType;
import org.springframework.modulith.core.ArchitecturallyEvidentType.ReferenceMethod;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.EventType;
import org.springframework.modulith.core.FormattableType;
import org.springframework.modulith.core.Source;
import org.springframework.modulith.core.SpringBean;
import org.springframework.modulith.docs.ConfigurationProperties.ModuleProperty;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.util.BuildSystemUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * @author Oliver Drotbohm
 */
class Asciidoctor {

	private static String PLACEHOLDER = "¯\\_(ツ)_/¯";
	private static final Pattern JAVADOC_CODE = Pattern.compile("\\{\\@(link|code|literal)\\s*(.*?)\\}");
	private static final Logger LOG = LoggerFactory.getLogger(Asciidoctor.class);

	private static final Optional<DocumentationSource> DOC_SOURCE = getSpringModulithDocsSource()
			.or(() -> getSpringAutoRestDocsSource());

	private final ApplicationModules modules;
	private final String javaDocBase;
	private final Optional<DocumentationSource> docSource;

	private Asciidoctor(ApplicationModules modules, String javaDocBase) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.hasText(javaDocBase, "Javadoc base must not be null or empty!");

		this.javaDocBase = javaDocBase;
		this.modules = modules;
		this.docSource = DOC_SOURCE.map(it -> new CodeReplacingDocumentationSource(it, this));
	}

	/**
	 * Creates a new {@link Asciidoctor} instance for the given {@link ApplicationModules} and Javadoc base URI.
	 *
	 * @param modules must not be {@literal null}.
	 * @param javadocBase can be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Asciidoctor withJavadocBase(ApplicationModules modules, @Nullable String javadocBase) {
		return new Asciidoctor(modules, javadocBase == null ? PLACEHOLDER : javadocBase);
	}

	/**
	 * Creates a new {@link Asciidoctor} instance for the given {@link ApplicationModules}.
	 *
	 * @param modules must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public static Asciidoctor withoutJavadocBase(ApplicationModules modules) {
		return new Asciidoctor(modules, PLACEHOLDER);
	}

	/**
	 * Turns the given source string into inline code.
	 *
	 * @param source must not be {@literal null}.
	 * @return
	 */
	public String toInlineCode(String source) {

		var parts = source.split("#");
		var type = parts[0];
		var methodSignature = parts.length == 2 ? Optional.of(parts[1]) : Optional.<String> empty();

		return modules.getModuleByType(type)
				.flatMap(it -> it.getType(type))
				.map(it -> toOptionalLink(it, methodSignature))
				.orElseGet(() -> String.format("`%s`", type));
	}

	public String toInlineCode(JavaClass type) {
		return toOptionalLink(type);
	}

	public String toInlineCode(SpringBean bean) {

		var type = bean.toArchitecturallyEvidentType();
		var base = toInlineCode(type);
		var interfaces = bean.getInterfacesWithinModule();

		if (interfaces.isEmpty()) {
			return base;
		}

		var interfacesAsString = interfaces.stream() //
				.map(this::toInlineCode) //
				.collect(Collectors.joining(", "));

		return "%s (via %s)".formatted(interfacesAsString, base);
	}

	private String withDocumentation(String base, JavaClass type) {

		return docSource.flatMap(it -> it.getDocumentation(type))
				.map(it -> base + " -- " + it)
				.orElse(base);
	}

	public String renderSpringBeans(ApplicationModule module, CanvasOptions options) {

		var builder = new StringBuilder();
		var groupings = options.groupBeans(module);

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

	public String renderPublishedEvents(ApplicationModule module) {

		var events = module.getPublishedEvents();

		if (events.isEmpty()) {
			return "none";
		}

		var builder = new StringBuilder();

		for (EventType eventType : events) {

			var documentation = docSource.flatMap(it -> it.getDocumentation(eventType.getType()))
					.map(" -- "::concat);

			builder.append("* ")
					.append(toInlineCode(eventType.getType()));
			documentation.ifPresent(builder::append);

			if (!eventType.hasSources()) {
				builder.append("\n");
			} else {
				builder.append((documentation.isPresent() ? " C" : " c") + "reated by:\n");
			}

			for (Source source : eventType.getSources()) {

				builder.append("** ")
						.append(toInlineCode(source.toString(module)))
						.append("\n");
			}
		}

		return builder.toString();
	}

	public String renderEventsListenedTo(ApplicationModule module) {

		return module.getSpringBeans().stream()
				.map(SpringBean::toArchitecturallyEvidentType)
				.filter(ArchitecturallyEvidentType::isEventListener)
				.flatMap(ArchitecturallyEvidentType::getReferenceMethods)
				.map(it -> renderReferenceMethod(it, 0))
				.collect(Collectors.joining(System.lineSeparator()));
	}

	public String renderConfigurationProperties(List<ModuleProperty> properties) {

		if (properties.isEmpty()) {
			return "none";
		}

		Stream<String> stream = properties.stream()
				.map(it -> {

					var builder = new StringBuilder()
							.append(toCode(it.name()))
							.append(" -- ")
							.append(toInlineCode(it.type()));

					var defaultValue = it.defaultValue();

					if (defaultValue != null && StringUtils.hasText(defaultValue)) {

						builder = builder.append(", default ")
								.append(toInlineCode(defaultValue));
					}

					var description = it.description();

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

		return toBulletPoints(types.stream()
				.map(it -> withDocumentation(toOptionalLink(it), it)));
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

		var module = modules.getModuleByType(source).orElse(null);
		var formattable = FormattableType.of(source).getAbbreviatedFullName(module);
		var typeAndMethod = toCode(toTypeAndMethod(formattable, methodSignature));

		if (module == null
				|| !source.getModifiers().contains(JavaModifier.PUBLIC)
				|| !module.contains(source)) {
			return typeAndMethod;
		}

		var classPath = convertClassNameToResourcePath(source.getFullName()) //
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

		var javaType = type.getType();
		var code = toInlineCode(javaType);

		if (type.isEventListener()) {

			if (!docSource.isPresent()) {

				var referenceTypes = type.getReferenceTypes();

				return String.format("%s listening to %s", //
						toInlineCode(javaType), //
						toInlineCode(referenceTypes));
			}

			String header = "%s listening to:\n".formatted(withDocumentation(code, javaType));

			return header + type.getReferenceMethods().map(it -> renderReferenceMethod(it, 1))
					.collect(Collectors.joining("\n"));
		}

		return withDocumentation(toInlineCode(type.getType()), type.getType());
	}

	private String renderReferenceMethod(ReferenceMethod it, int level) {

		var method = it.getMethod();
		Assert.isTrue(method.getRawParameterTypes().size() > 0,
				() -> String.format("Method %s must have at least one parameter!", method));

		var parameterType = method.getRawParameterTypes().get(0);
		var isAsync = it.isAsync() ? "(async) " : "";
		var indent = "*".repeat(level + 1);

		return docSource.flatMap(source -> source.getDocumentation(method))
				.map(doc -> String.format("%s %s %s-- %s", indent, toInlineCode(parameterType), isAsync, doc))
				.orElseGet(() -> String.format("%s %s %s", indent, toInlineCode(parameterType), isAsync));
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

			String type = matcher.group(2);

			source = source.replace(matcher.group(), toInlineCode(type));
		}

		return source;
	}

	/**
	 * @param module
	 * @return
	 */
	public String renderBeanReferences(ApplicationModule module) {

		var bullets = module.getDirectDependencies(modules, DependencyType.USES_COMPONENT)
				.uniqueStream(ApplicationModuleDependency::getTargetType)
				.map(it -> {

					var targetType = it.getTargetType();
					var result = "%s (in %s)".formatted(toInlineCode(targetType), it.getTargetModule().getDisplayName());

					return withDocumentation(result, targetType);
				})
				.map(this::toBulletPoint)
				.collect(Collectors.joining("\n"));

		return bullets.isBlank() ? "None" : bullets;
	}

	public String renderHeadline(int i, String modules) {
		return "=".repeat(i) + " " + modules + System.lineSeparator();
	}

	public String renderPlantUmlInclude(String componentsFilename) {
		return "plantuml::" + componentsFilename + "[,,format=svg]" + System.lineSeparator();
	}

	public String renderGeneralInclude(String componentsFilename) {
		return "include::" + componentsFilename + "[]" + System.lineSeparator();
	}

	@SuppressWarnings("deprecation")
	private static Optional<DocumentationSource> getSpringAutoRestDocsSource() {

		return BuildSystemUtils.getTargetResource("generated-javadoc-json")
				.map(__ -> SpringAutoRestDocsDocumentationSource.INSTANCE)
				.map(it -> {
					LOG.debug("Using Javadoc generated by Spring Auto RESTDocs found in generated-javadoc-json.");
					LOG.warn(
							"Javadoc metadata generated by Spring Auto RESTDocs is deprecated! Switch to spring-modulith-apt instead!");
					return it;
				});
	}

	private static Optional<DocumentationSource> getSpringModulithDocsSource() {

		return SpringModulithDocumentationSource.getInstance()
				.map(it -> {
					LOG.debug("Using Javadoc extracted by Spring Modulith in {}.",
							SpringModulithDocumentationSource.getMetadataLocation());
					return it;
				});
	}
}
