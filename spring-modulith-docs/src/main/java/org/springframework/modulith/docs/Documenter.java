/*
 * Copyright 2018-2022 the original author or authors.
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

import static org.springframework.modulith.docs.Asciidoctor.*;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.modulith.model.ApplicationModule;
import org.springframework.modulith.model.ApplicationModule.DependencyDepth;
import org.springframework.modulith.model.ApplicationModule.DependencyType;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.model.SpringBean;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.structurizr.Workspace;
import com.structurizr.io.Diagram;
import com.structurizr.io.plantuml.BasicPlantUMLWriter;
import com.structurizr.io.plantuml.C4PlantUMLExporter;
import com.structurizr.io.plantuml.PlantUMLWriter;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Model;
import com.structurizr.model.Relationship;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.model.Tags;
import com.structurizr.view.ComponentView;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.Shape;
import com.structurizr.view.Styles;
import com.structurizr.view.View;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * API to create documentation for {@link ApplicationModules}.
 *
 * @author Oliver Drotbohm
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Documenter {

	private static final Map<DependencyType, String> DEPENDENCY_DESCRIPTIONS = new LinkedHashMap<>();

	private static final String INVALID_FILE_NAME_PATTERN = "Configured file name pattern does not include a '%s' placeholder for the module name!";

	static {
		DEPENDENCY_DESCRIPTIONS.put(DependencyType.EVENT_LISTENER, "listens to");
		DEPENDENCY_DESCRIPTIONS.put(DependencyType.DEFAULT, "depends on");
	}

	private final @Getter ApplicationModules modules;
	private final Workspace workspace;
	private final Container container;
	private final ConfigurationProperties properties;
	private final String outputFolder;

	private Map<ApplicationModule, Component> components;

	/**
	 * Creates a new {@link Documenter} for the {@link ApplicationModules} created for the given modulith type.
	 *
	 * @param modulithType must not be {@literal null}.
	 */
	public Documenter(Class<?> modulithType) {
		this(ApplicationModules.of(modulithType));
	}

	/**
	 * Creates a new {@link Documenter} for the given {@link ApplicationModules} instance.
	 *
	 * @param modules must not be {@literal null}.
	 */
	public Documenter(ApplicationModules modules) {
		this(modules, getDefaultOutputDirectory());
	}

	private Documenter(ApplicationModules modules, String outputFolder) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.hasText(outputFolder, "Output folder must not be null or empty!");

		this.modules = modules;
		this.outputFolder = outputFolder;
		this.workspace = new Workspace("Modulith", "");

		workspace.getViews().getConfiguration()
				.getStyles()
				.addElementStyle(Tags.COMPONENT)
				.shape(Shape.Component);

		Model model = workspace.getModel();
		String systemName = modules.getSystemName().orElse("Modulith");

		SoftwareSystem system = model.addSoftwareSystem(systemName, "");

		this.container = system.addContainer("Application", "", "");
		this.properties = new ConfigurationProperties();
	}

	private Map<ApplicationModule, Component> getComponents(Options options) {

		if (components == null) {

			this.components = modules.stream() //
					.collect(Collectors.toMap(Function.identity(),
							it -> container.addComponent(options.getDefaultDisplayName().apply(it), "", "Module")));

			this.components.forEach((key, value) -> addDependencies(key, value, options));
		}

		return components;
	}

	/**
	 * Customize the output folder to write the generated files to. Defaults to {@value #DEFAULT_LOCATION}.
	 *
	 * @param outputFolder must not be {@literal null} or empty.
	 * @return
	 * @see #DEFAULT_LOCATION
	 */
	public Documenter withOutputFolder(String outputFolder) {
		return new Documenter(modules, workspace, container, properties, outputFolder, components);
	}

	/**
	 * Writes all available documentation:
	 * <ul>
	 * <li>The entire set of modules as overview component diagram.</li>
	 * <li>Individual component diagrams per module to include all upstream modules.</li>
	 * <li>The Module Canvas for each module.</li>
	 * </ul>
	 *
	 * @param options must not be {@literal null}, use {@link Options#defaults()} for default.
	 * @param canvasOptions must not be {@literal null}, use {@link CanvasOptions#defaults()} for default.
	 * @return the current instance, will never be {@literal null}.
	 * @throws IOException
	 */
	public Documenter writeDocumentation(Options options, CanvasOptions canvasOptions) throws IOException {

		return writeModulesAsPlantUml(options)
				.writeIndividualModulesAsPlantUml(options) //
				.writeModuleCanvases(canvasOptions);
	}

	/**
	 * Writes the PlantUML component diagram for all {@link ApplicationModules}.
	 *
	 * @param options must not be {@literal null}.
	 * @throws IOException
	 */
	public Documenter writeModulesAsPlantUml(Options options) throws IOException {

		Assert.notNull(options, "Options must not be null!");

		Path file = recreateFile(options.getTargetFileName().orElse("components.uml"));

		try (Writer writer = new FileWriter(file.toFile())) {
			writer.write(createPlantUml(options));
		}

		return this;
	}

	/**
	 * Writes the component diagrams for all individual modules.
	 *
	 * @param options must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeIndividualModulesAsPlantUml(Options options) {

		modules.forEach(it -> writeModuleAsPlantUml(it, options));

		return this;
	}

	/**
	 * Writes the PlantUML component diagram for the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleAsPlantUml(ApplicationModule module) {

		Assert.notNull(module, "Module must not be null!");

		return writeModuleAsPlantUml(module, Options.defaults());
	}

	/**
	 * Writes the PlantUML component diagram for the given {@link ApplicationModule} with the given rendering {@link Options}.
	 *
	 * @param module must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleAsPlantUml(ApplicationModule module, Options options) {

		Assert.notNull(module, "Module must not be null!");
		Assert.notNull(options, "Options must not be null!");

		ComponentView view = createComponentView(options, module);
		view.setTitle(options.getDefaultDisplayName().apply(module));

		addComponentsToView(module, view, options);

		String fileNamePattern = options.getTargetFileName().orElse("module-%s.uml");

		Assert.isTrue(fileNamePattern.contains("%s"), () -> String.format(INVALID_FILE_NAME_PATTERN, fileNamePattern));

		return writeViewAsPlantUml(view, String.format(fileNamePattern, module.getName()), options);
	}

	/**
	 * Writes all module canvases using {@link Options#defaults()}.
	 *
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleCanvases() {
		return writeModuleCanvases(CanvasOptions.defaults());
	}

	public Documenter writeModuleCanvases(CanvasOptions options) {

		modules.forEach(module -> {

			String filename = String.format(options.getTargetFileName().orElse("module-%s.adoc"), module.getName());
			Path file = recreateFile(filename);

			try (FileWriter writer = new FileWriter(file.toFile())) {

				writer.write(toModuleCanvas(module, options));

			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}
		});

		return this;
	}

	public String toModuleCanvas(ApplicationModule module) {
		return toModuleCanvas(module, CanvasOptions.defaults());
	}

	public String toModuleCanvas(ApplicationModule module, String apiBase) {
		return toModuleCanvas(module, CanvasOptions.defaults().withApiBase(apiBase));
	}

	public String toModuleCanvas(ApplicationModule module, CanvasOptions options) {

		Asciidoctor asciidoctor = Asciidoctor.withJavadocBase(modules, options.getApiBase());
		Function<List<JavaClass>, String> mapper = asciidoctor::typesToBulletPoints;

		StringBuilder builder = new StringBuilder();
		builder.append(startTable("%autowidth.stretch, cols=\"h,a\""));
		builder.append(writeTableRow("Base package", asciidoctor.toInlineCode(module.getBasePackage().getName())));
		builder.append(writeTableRow("Spring components", asciidoctor.renderSpringBeans(options, module)));
		builder.append(addTableRow(module.getAggregateRoots(), "Aggregate roots", mapper));
		builder.append(writeTableRow("Published events", asciidoctor.renderEvents(module)));
		builder.append(addTableRow(module.getEventsListenedTo(modules), "Events listened to", mapper));
		builder.append(writeTableRow("Properties",
				asciidoctor.renderConfigurationProperties(module, properties.getModuleProperties(module))));
		builder.append(startOrEndTable());

		return builder.toString();
	}

	private <T> String addTableRow(List<T> types, String header, Function<List<T>, String> mapper) {
		return types.isEmpty() ? "" : writeTableRow(header, mapper.apply(types));
	}

	public String toPlantUml() throws IOException {
		return createPlantUml(Options.defaults());
	}

	private void addDependencies(ApplicationModule module, Component component, Options options) {

		DEPENDENCY_DESCRIPTIONS.entrySet().stream().forEach(entry -> {

			module.getDependencies(modules, entry.getKey()).stream() //
					.map(it -> getComponents(options).get(it)) //
					// .filter(it -> !component.hasEfferentRelationshipWith(it)) //
					.forEach(it -> {

						Relationship relationship = component.uses(it, entry.getValue());
						relationship.addTags(entry.getKey().toString());
					});
		});

		module.getBootstrapDependencies(modules) //
				.forEach(it -> {
					Relationship relationship = component.uses(getComponents(options).get(it), "uses");
					relationship.addTags(DependencyType.USES_COMPONENT.toString());
				});
	}

	private void addComponentsToView(ApplicationModule module, ComponentView view, Options options) {

		Supplier<Stream<ApplicationModule>> bootstrapDependencies = () -> module.getBootstrapDependencies(modules,
				options.getDependencyDepth());
		Supplier<Stream<ApplicationModule>> otherDependencies = () -> options.getDependencyTypes()
				.flatMap(it -> module.getDependencies(modules, it).stream());

		Supplier<Stream<ApplicationModule>> dependencies = () -> Stream.concat(bootstrapDependencies.get(), otherDependencies.get());

		addComponentsToView(dependencies, view, options, it -> it.add(getComponents(options).get(module)));
	}

	private void addComponentsToView(Supplier<Stream<ApplicationModule>> modules, ComponentView view, Options options,
			Consumer<ComponentView> afterCleanup) {

		Styles styles = view.getViewSet().getConfiguration().getStyles();
		Map<ApplicationModule, Component> components = getComponents(options);

		modules.get() //
				.distinct()
				.filter(options.getExclusions().negate()) //
				.map(it -> applyBackgroundColor(it, components, options, styles)) //
				.filter(options.getComponentFilter()) //
				.forEach(view::add);

		// view.getViewSet().getConfiguration().getStyles().findElementStyle(element).getBackground()

		// Remove filtered dependency types
		DependencyType.allBut(options.getDependencyTypes()) //
				.map(Object::toString) //
				.forEach(it -> view.removeRelationshipsWithTag(it));

		afterCleanup.accept(view);

		// Filter outgoing relationships of target-only modules
		modules.get().filter(options.getTargetOnly()) //
				.forEach(module -> {

					Component component = components.get(module);

					view.getRelationships().stream() //
							.map(RelationshipView::getRelationship) //
							.filter(it -> it.getSource().equals(component)) //
							.forEach(it -> view.remove(it));
				});

		// … as well as all elements left without a relationship
		if (options.hideElementsWithoutRelationships()) {
			view.removeElementsWithNoRelationships();
		}

		afterCleanup.accept(view);

		// Remove default relationships if more qualified ones exist
		view.getRelationships().stream() //
				.map(RelationshipView::getRelationship) //
				.collect(Collectors.groupingBy(Connection::of)) //
				.values().stream() //
				.forEach(it -> potentiallyRemoveDefaultRelationship(view, it));
	}

	private void potentiallyRemoveDefaultRelationship(View view, Collection<Relationship> relationships) {

		if (relationships.size() <= 1) {
			return;
		}

		relationships.stream().filter(it -> it.getTagsAsSet().contains(DependencyType.DEFAULT.toString())) //
				.findFirst().ifPresent(view::remove);
	}

	private static Component applyBackgroundColor(ApplicationModule module, Map<ApplicationModule, Component> components, Options options,
			Styles styles) {

		Component component = components.get(module);
		Function<ApplicationModule, Optional<String>> selector = options.getColorSelector();

		// Apply custom color if configured
		selector.apply(module).ifPresent(color -> {

			String tag = module.getName() + "-" + color;
			component.addTags(tag);

			// Add or update background color
			styles.getElements().stream()
					.filter(it -> it.getTag().equals(tag))
					.findFirst()
					.orElseGet(() -> styles.addElementStyle(tag))
					.background(color);
		});

		return component;
	}

	private Documenter writeViewAsPlantUml(ComponentView view, String filename, Options options) {

		Path file = recreateFile(filename);

		try (Writer writer = new FileWriter(file.toFile())) {

			writer.write(render(view, options));

			return this;

		} catch (IOException o_O) {
			throw new RuntimeException(o_O);
		}
	}

	private String render(ComponentView view, Options options) {

		switch (options.style) {

			case C4:

				C4PlantUMLExporter exporter = new C4PlantUMLExporter();
				Diagram diagram = exporter.export(view);
				return diagram.getDefinition();

			case UML:
			default:

				Writer writer = new StringWriter();
				PlantUMLWriter umlWriter = new BasicPlantUMLWriter();
				umlWriter.addSkinParam("componentStyle", "uml1");
				umlWriter.write(view, writer);

				return writer.toString();
		}
	}

	private String createPlantUml(Options options) throws IOException {

		ComponentView componentView = createComponentView(options);
		componentView.setTitle(modules.getSystemName().orElse("Modules"));

		addComponentsToView(() -> modules.stream(), componentView, options, it -> {});

		return render(componentView, options);
	}

	private ComponentView createComponentView(Options options) {
		return createComponentView(options, null);
	}

	private ComponentView createComponentView(Options options, @Nullable ApplicationModule module) {

		String prefix = module == null ? "modules-" : module.getName();

		return workspace.getViews() //
				.createComponentView(container, prefix + options.toString(), "");
	}

	private Path recreateFile(String name) {

		try {

			Files.createDirectories(Paths.get(outputFolder));
			Path filePath = Paths.get(outputFolder, name);
			Files.deleteIfExists(filePath);

			return Files.createFile(filePath);

		} catch (IOException o_O) {
			throw new RuntimeException(o_O);
		}
	}

	/**
	 * Returns the default output directory based on the detected build system.
	 *
	 * @return will never be {@literal null}.
	 */
	private static String getDefaultOutputDirectory() {
		return (new File("pom.xml").exists() ? "target" : "build").concat("/spring-modulith-docs");
	}

	@Value
	private static class Connection {

		Element source, target;

		public static Connection of(Relationship relationship) {
			return new Connection(relationship.getSource(), relationship.getDestination());
		}
	}

	/**
	 * Options to tweak the rendering of diagrams.
	 *
	 * @author Oliver Drotbohm
	 */
	@Getter(AccessLevel.PRIVATE)
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Options {

		private static final Set<DependencyType> ALL_TYPES = Arrays.stream(DependencyType.values()).collect(Collectors.toSet());

		private final Set<DependencyType> dependencyTypes;

		/**
		 * The {@link DependencyDepth} to define which other modules to be included in the diagram to be created.
		 */
		private final @With DependencyDepth dependencyDepth;

		/**
		 * A {@link Predicate} to define the which modules to exclude from the diagram to be created.
		 */
		private final @With Predicate<ApplicationModule> exclusions;

		/**
		 * A {@link Predicate} to define which Structurizr {@link Component}s to be included in the diagram to be created.
		 */
		private final @With Predicate<Component> componentFilter;

		/**
		 * A {@link Predicate} to define which of the modules shall only be considered targets, i.e. all efferent
		 * relationships are going to be hidden from the rendered view. Modules that have no incoming relationships will
		 * entirely be removed from the view.
		 */
		private final @With Predicate<ApplicationModule> targetOnly;

		/**
		 * The target file name to be used for the diagram to be created. For individual module diagrams this needs to
		 * include a {@code %s} placeholder for the module names.
		 */
		private final @With @Nullable String targetFileName;

		/**
		 * A callback to return a hex-encoded color per {@link ApplicationModule}.
		 */
		private final @With Function<ApplicationModule, Optional<String>> colorSelector;

		/**
		 * A callback to return a default display names for a given {@link ApplicationModule}. Default implementation just forwards to
		 * {@link ApplicationModule#getDisplayName()}.
		 */
		private final @With Function<ApplicationModule, String> defaultDisplayName;

		/**
		 * Which style to render the diagram in. Defaults to {@value DiagramStyle#UML}.
		 */
		private final @With DiagramStyle style;

		/**
		 * Configuration setting to define whether modules that do not have a relationship to any other module shall be
		 * retained in the diagrams created. The default is {@value ElementsWithoutRelationships#HIDDEN}. See
		 * {@link Options#withExclusions(Predicate)} for a more fine-grained way of defining which modules to exclude in
		 * case you flip this to {@link ElementsWithoutRelationships#VISIBLE}.
		 *
		 * @see #withExclusions(Predicate)
		 */
		private final @With ElementsWithoutRelationships elementsWithoutRelationships;

		/**
		 * Creates a new default {@link Options} instance configured to use all dependency types, list immediate
		 * dependencies for individual module instances, not applying any kind of {@link ApplicationModule} or {@link Component}
		 * filters and default file names.
		 *
		 * @return will never be {@literal null}.
		 */
		public static Options defaults() {
			return new Options(ALL_TYPES, DependencyDepth.IMMEDIATE, it -> false, it -> true, it -> false, null,
					__ -> Optional.empty(), it -> it.getDisplayName(), DiagramStyle.UML, ElementsWithoutRelationships.HIDDEN);
		}

		/**
		 * Select the dependency types that are supposed to be included in the diagram to be created.
		 *
		 * @param types must not be {@literal null}.
		 * @return
		 */
		public Options withDependencyTypes(DependencyType... types) {

			Assert.notNull(types, "Dependency types must not be null!");

			Set<DependencyType> dependencyTypes = Arrays.stream(types).collect(Collectors.toSet());

			return new Options(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly, targetFileName,
					colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		private Optional<String> getTargetFileName() {
			return Optional.ofNullable(targetFileName);
		}

		private Stream<DependencyType> getDependencyTypes() {
			return dependencyTypes.stream();
		}

		private boolean hideElementsWithoutRelationships() {
			return elementsWithoutRelationships.equals(ElementsWithoutRelationships.HIDDEN);
		}

		/**
		 * Different diagram styles.
		 *
		 * @author Oliver Drotbohm
		 */
		public enum DiagramStyle {

			/**
			 * A plain UML component diagram.
			 */
			UML,

			/**
			 * A C4 model component diagram.
			 *
			 * @see https://c4model.com/#ComponentDiagram
			 */
			C4;
		}

		/**
		 * Configuration setting to define whether modules that do not have a relationship to any other module shall be
		 * retained in the diagrams created. The default is {@value ElementsWithoutRelationships#HIDDEN}. See
		 * {@link Options#withExclusions(Predicate)} for a more fine-grained way of defining which modules to exclude in
		 * case you flip this to {@link ElementsWithoutRelationships#VISIBLE}.
		 *
		 * @author Oliver Drotbohm
		 * @see Options#withExclusions(Predicate)
		 */
		public enum ElementsWithoutRelationships {
			HIDDEN, VISIBLE;
		}
	}

	// Prefix required for javac 🤔
	@lombok.RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public static class CanvasOptions {

		static final Grouping FALLBACK_GROUP = Grouping.of("Others", null, __ -> true);

		private final List<Grouping> groupers;
		private final @With @Getter @Nullable String apiBase;
		private final @With @Nullable String targetFileName;

		public static CanvasOptions defaults() {

			return withoutDefaultGroupings()
					.groupingBy("Controllers", bean -> bean.toArchitecturallyEvidentType().isController()) //
					.groupingBy("Services", bean -> bean.toArchitecturallyEvidentType().isService()) //
					.groupingBy("Repositories", bean -> bean.toArchitecturallyEvidentType().isRepository()) //
					.groupingBy("Event listeners", bean -> bean.toArchitecturallyEvidentType().isEventListener()) //
					.groupingBy("Configuration properties",
							bean -> bean.toArchitecturallyEvidentType().isConfigurationProperties());
		}

		public static CanvasOptions withoutDefaultGroupings() {
			return new CanvasOptions(new ArrayList<>(), null, null);
		}

		public CanvasOptions groupingBy(Grouping... groupings) {

			List<Grouping> result = new ArrayList<>(groupers);
			result.addAll(Arrays.asList(groupings));

			return new CanvasOptions(result, apiBase, targetFileName);
		}

		public CanvasOptions groupingBy(String name, Predicate<SpringBean> filter) {
			return groupingBy(Grouping.of(name, null, filter));
		}

		Groupings groupBeans(ApplicationModule module) {

			List<Grouping> sources = new ArrayList<>(groupers);
			sources.add(FALLBACK_GROUP);

			MultiValueMap<Grouping, SpringBean> result = new LinkedMultiValueMap<>();
			List<SpringBean> alreadyMapped = new ArrayList<>();

			sources.forEach(it -> {

				List<SpringBean> matchingBeans = getMatchingBeans(module, it, alreadyMapped);

				result.addAll(it, matchingBeans);
				alreadyMapped.addAll(matchingBeans);
			});

			// Wipe entries without any beans
			new HashSet<>(result.keySet()).forEach(key -> {
				if (result.get(key).isEmpty()) {
					result.remove(key);
				}
			});

			return Groupings.of(result);
		}

		private Optional<String> getTargetFileName() {
			return Optional.ofNullable(targetFileName);
		}

		private static List<SpringBean> getMatchingBeans(ApplicationModule module, Grouping filter, List<SpringBean> alreadyMapped) {

			return module.getSpringBeans().stream()
					.filter(it -> !alreadyMapped.contains(it))
					.filter(filter::matches)
					.collect(Collectors.toList());
		}

		@Value(staticConstructor = "of")
		@Getter(AccessLevel.PACKAGE)
		public static class Grouping {

			String name;
			@Nullable String description;
			Predicate<SpringBean> predicate;

			public static Grouping of(String name) {
				return new Grouping(name, null, __ -> false);
			}

			public static Grouping of(String name, Predicate<SpringBean> predicate) {
				return new Grouping(name, null, predicate);
			}

			public boolean matches(SpringBean candidate) {
				return predicate.test(candidate);
			}

			public static Predicate<SpringBean> nameMatching(String pattern) {
				return bean -> bean.getFullyQualifiedTypeName().matches(pattern);
			}

			public static Predicate<SpringBean> implementing(Class<?> type) {
				return bean -> bean.getType().isAssignableTo(type);
			}

			public static Predicate<SpringBean> subtypeOf(Class<?> type) {
				return implementing(type) //
						.and(bean -> !bean.getType().isEquivalentTo(type));
			}
		}

		@RequiredArgsConstructor(access = AccessLevel.PACKAGE, staticName = "of")
		static class Groupings {

			private final MultiValueMap<Grouping, SpringBean> groupings;

			Set<Grouping> keySet() {
				return groupings.keySet();
			}

			List<SpringBean> byGrouping(Grouping grouping) {
				return byFilter(grouping::equals);
			}

			List<SpringBean> byGroupName(String name) {
				return byFilter(it -> it.getName().equals(name));
			}

			void forEach(BiConsumer<Grouping, List<SpringBean>> consumer) {
				groupings.forEach(consumer);
			}

			private List<SpringBean> byFilter(Predicate<Grouping> filter) {

				return groupings.entrySet().stream()
						.filter(it -> filter.test(it.getKey()))
						.findFirst()
						.map(Entry::getValue)
						.orElseGet(Collections::emptyList);
			}

			boolean hasOnlyFallbackGroup() {
				return groupings.size() == 1 && groupings.get(FALLBACK_GROUP) != null;
			}
		}
	}
}
